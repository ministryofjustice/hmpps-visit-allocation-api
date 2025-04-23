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
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReleasedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension.Companion.prisonApiMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate

@DisplayName("Test for Domain Event Prisoner Released")
class DomainEventsPrisonerReleasedTest : EventsIntegrationTestBase() {

  @Test
  fun `when domain event prisoner released is found, then prisoner balance is synced`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    entityHelper.createPrisonerDetails(prisonerId = prisonerId)
    entityHelper.createAndSaveVisitOrders(prisonerId = prisonerId, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = prisonerId, VisitOrderType.PVO, 1)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_RELEASED_EVENT_TYPE.value,
      createPrisonerReleasedAdditionalInformationJson(prisonerId, prisonId, PrisonerReleasedReasonType.RELEASED),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_RELEASED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(0, 0))

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerBalanceFromEventChange(any(), any()) }
    await untilAsserted { verify(changeLogService, times(1)).logSyncEventChange(any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(0)
  }

  @Test
  fun `when domain event prisoner released is found, but an error is returned from prison-api the message is sent to DLQ`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    entityHelper.createPrisonerDetails(prisonerId = prisonerId)
    entityHelper.createAndSaveVisitOrders(prisonerId = prisonerId, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = prisonerId, VisitOrderType.PVO, 1)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_RELEASED_EVENT_TYPE.value,
      createPrisonerReleasedAdditionalInformationJson(prisonerId, prisonId, PrisonerReleasedReasonType.RELEASED),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_RELEASED_EVENT_TYPE.value, domainEvent)

    // And
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, null)

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

    entityHelper.createPrisonerDetails(prisonerId = prisonerId)
    entityHelper.createAndSaveVisitOrders(prisonerId = prisonerId, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = prisonerId, VisitOrderType.PVO, 1)

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
  fun `when domain event prisoner released is found, then new prisoner balance is synced`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_RELEASED_EVENT_TYPE.value,
      createPrisonerReleasedAdditionalInformationJson(prisonerId, prisonId, PrisonerReleasedReasonType.RELEASED),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_RELEASED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(0, 0))

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerBalanceFromEventChange(any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(0)

    val prisonerDetails = prisonerDetailsRepository.findByPrisonerId(prisonerId)!!
    assertThat(prisonerDetails.lastVoAllocatedDate).isEqualTo(LocalDate.now())
    assertThat(prisonerDetails.lastPvoAllocatedDate).isNull()
  }
}
