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
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension.Companion.prisonApiMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate

@DisplayName("Test for Domain Event Prisoner Merged")
class DomainEventsPrisonerMergedTest : EventsIntegrationTestBase() {

  @Test
  fun `when domain event prisoner merged is found, then prisoner balance is synced`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "HEI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerBalanceFromEventChange(any(), any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerRemoved(any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogSyncEventChange(any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(5)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails[0].prisonerId).isEqualTo(prisonerId)
  }

  @Test
  fun `when domain event prisoner merged is found, but an error is returned from prisoner search the message is sent to DLQ`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, null, HttpStatus.NOT_FOUND)
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(0, 0))

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }

  @Test
  fun `when domain event prisoner merged is found, but a 404 not found is returned from prison API then message is skipped`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, null, HttpStatus.NOT_FOUND)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 0 }
  }

  @Test
  fun `when domain event prisoner merged is found, then new prisoner balance is synced`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "HEI"

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerBalanceFromEventChange(any(), any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerRemoved(any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogSyncEventChange(any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(5)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails[0].prisonerId).isEqualTo(prisonerId)
    assertThat(prisonerDetails[0].lastVoAllocatedDate).isEqualTo(LocalDate.now())
    assertThat(prisonerDetails[0].lastPvoAllocatedDate).isNull()
  }
}
