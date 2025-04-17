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
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension.Companion.prisonApiMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers.DomainEventHandlerRegistry.Companion.PRISONER_RELEASED_EVENT_TYPE
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

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
      PRISONER_RELEASED_EVENT_TYPE,
      createPrisonerReleasedAdditionalInformationJson(prisonerId, prisonId, "released"),
    )
    val publishRequest = createDomainEventPublishRequest(PRISONER_RELEASED_EVENT_TYPE, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, PrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, VisitBalancesDto(remainingVo = 0, remainingPvo = 0))

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerBalanceFromEventChange(any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(0)
  }

  @Test
  fun `when domain event prisoner released is found, but an error is returned from prisoner search the message is sent to DLQ`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    entityHelper.createPrisonerDetails(prisonerId = prisonerId)
    entityHelper.createAndSaveVisitOrders(prisonerId = prisonerId, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = prisonerId, VisitOrderType.PVO, 1)

    val domainEvent = createDomainEventJson(
      PRISONER_RELEASED_EVENT_TYPE,
      createPrisonerReleasedAdditionalInformationJson(prisonerId, prisonId, "released"),
    )
    val publishRequest = createDomainEventPublishRequest(PRISONER_RELEASED_EVENT_TYPE, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, null, HttpStatus.NOT_FOUND)
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, VisitBalancesDto(remainingVo = 0, remainingPvo = 0))

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }
}
