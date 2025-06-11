package uk.gov.justice.digital.hmpps.visitallocationapi.integration.events

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension.Companion.prisonApiMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate

@DisplayName("Test for Domain Event Prisoner Received")
class DomainEventsPrisonerReceivedTest : EventsIntegrationTestBase() {

  @Test
  fun `when domain event prisoner received is found, then prisoner balance is synced for nomis prison`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value,
      createPrisonerReceivedAdditionalInformationJson(prisonerId, prisonId, PrisonerReceivedReasonType.NEW_ADMISSION),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerBalanceFromEventChange(any(), any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogSyncEventChange(any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(5)
  }

  @Test
  fun `when domain event prisoner received is found with supported reset reason, then prisoner balance is reset for dps prison`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value,
      createPrisonerReceivedAdditionalInformationJson(prisonerId, prisonId, PrisonerReceivedReasonType.NEW_ADMISSION),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(2)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(2)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerReceivedResetBalance(any(), any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogPrisonerBalanceReset(any(), any()) }
    await untilAsserted { verify(snsService, times(1)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(0)

    val changeLogs = changeLogRepository.findAll()
    assertThat(changeLogs.size).isEqualTo(1)
    assertThat(changeLogs.first().changeType).isEqualTo(ChangeLogType.PRISONER_BALANCE_RESET)
  }

  @Test
  fun `when domain event prisoner received is found, but an error is returned from prison API the message is sent to DLQ`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value,
      createPrisonerReceivedAdditionalInformationJson(prisonerId, prisonId, PrisonerReceivedReasonType.NEW_ADMISSION),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value, domainEvent)

    // And
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, null, HttpStatus.INTERNAL_SERVER_ERROR)
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }

  @Test
  fun `when domain event prisoner received is found, but a 404 not found is returned from prison API then message is skipped`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value,
      createPrisonerReceivedAdditionalInformationJson(prisonerId, prisonId, PrisonerReceivedReasonType.NEW_ADMISSION),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value, domainEvent)

    // And
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, null, HttpStatus.NOT_FOUND)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 0 }
  }

  @Test
  fun `when domain event prisoner received is found, then new prisoner balance is synced`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value,
      createPrisonerReceivedAdditionalInformationJson(prisonerId, prisonId, PrisonerReceivedReasonType.NEW_ADMISSION),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerBalanceFromEventChange(any(), any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogSyncEventChange(any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(5)

    val prisonerDetails = prisonerDetailsRepository.findById(prisonerId).get()
    assertThat(prisonerDetails.lastVoAllocatedDate).isEqualTo(LocalDate.now().minusDays(14))
    assertThat(prisonerDetails.lastPvoAllocatedDate).isNull()
  }

  @Test
  fun `when domain event prisoner received is found but 500 response from prison-api get service prison, then message fails`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value,
      createPrisonerReceivedAdditionalInformationJson(prisonerId, prisonId, PrisonerReceivedReasonType.NEW_ADMISSION),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_RECEIVED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false, HttpStatus.INTERNAL_SERVER_ERROR)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }
}
