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
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension.Companion.prisonApiMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers.DomainEventHandlerRegistry.Companion.PRISONER_BOOKING_MOVED_EVENT_TYPE
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

@DisplayName("Test for Domain Event Booking Moved")
class DomainEventsBookingMovedTest : EventsIntegrationTestBase() {

  @Test
  fun `when domain event booking moved is found, then prisoner balance is synced`() {
    // Given
    val movedFromPrisonerId = "AA123456"
    val movedToPrisonerId = "BB654321"
    val prisonId = "HEI"
    val lastPrisonId = "HEI"

    entityHelper.createPrisonerDetails(prisonerId = movedFromPrisonerId)
    entityHelper.createAndSaveVisitOrders(prisonerId = movedFromPrisonerId, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = movedFromPrisonerId, VisitOrderType.PVO, 1)

    entityHelper.createPrisonerDetails(prisonerId = movedToPrisonerId)
    entityHelper.createAndSaveVisitOrders(prisonerId = movedToPrisonerId, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = movedToPrisonerId, VisitOrderType.PVO, 1)

    val domainEvent = createDomainEventJson(
      PRISONER_BOOKING_MOVED_EVENT_TYPE,
      createPrisonerBookingMovedAdditionalInformationJson(movedFromPrisonerId = movedFromPrisonerId, movedToPrisonerId = movedToPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(PRISONER_BOOKING_MOVED_EVENT_TYPE, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = movedFromPrisonerId, createPrisonerDto(prisonerId = movedFromPrisonerId, prisonId = prisonId, inOutStatus = "IN", lastPrisonId = lastPrisonId))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = movedToPrisonerId, createPrisonerDto(prisonerId = movedToPrisonerId, prisonId = prisonId, inOutStatus = "IN", lastPrisonId = lastPrisonId))

    prisonApiMockServer.stubGetVisitBalances(prisonerId = movedFromPrisonerId, VisitBalancesDto(remainingVo = 0, remainingPvo = 0))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = movedToPrisonerId, VisitBalancesDto(remainingVo = 2, remainingPvo = 1))

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(nomisSyncService, times(2)).syncPrisonerBalanceFromEventChange(any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(3)
  }

  @Test
  fun `when domain event booking moved is found, but an error is returned from prisoner search the message is sent to DLQ`() {
    // Given
    val movedFromPrisonerId = "AA123456"
    val movedToPrisonerId = "BB654321"
    val prisonId = "HEI"
    val lastPrisonId = "HEI"

    entityHelper.createPrisonerDetails(prisonerId = movedFromPrisonerId)
    entityHelper.createAndSaveVisitOrders(prisonerId = movedFromPrisonerId, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = movedFromPrisonerId, VisitOrderType.PVO, 1)

    entityHelper.createPrisonerDetails(prisonerId = movedToPrisonerId)
    entityHelper.createAndSaveVisitOrders(prisonerId = movedToPrisonerId, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = movedToPrisonerId, VisitOrderType.PVO, 1)

    val domainEvent = createDomainEventJson(
      PRISONER_BOOKING_MOVED_EVENT_TYPE,
      createPrisonerBookingMovedAdditionalInformationJson(movedFromPrisonerId = movedFromPrisonerId, movedToPrisonerId = movedToPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(PRISONER_BOOKING_MOVED_EVENT_TYPE, domainEvent)

    // And
    prisonApiMockServer.stubGetVisitBalances(prisonerId = movedFromPrisonerId, null)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }
}
