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
import org.mockito.kotlin.verifyNoInteractions
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension.Companion.prisonApiMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate

@DisplayName("Test for Domain Event Prisoner Conviction Status Changed")
class DomainEventsPrisonerConvictionStatusChangedTest : EventsIntegrationTestBase() {

  @Test
  fun `when domain event prisoner conviction status changed is found, then prisoner balance is synced`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.CONVICTION_STATUS_UPDATED_EVENT_TYPE.value,
      createPrisonerConvictionStatusChangedAdditionalInformationJson(prisonerId = prisonerId, convictedStatus = "Convicted"),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.CONVICTION_STATUS_UPDATED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }

    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(0)
  }

  @Test
  fun `when domain event prisoner conviction status changed is found, but prisoner status is OUT then message is skipped`() {
    // Given
    val prisonerId = "AA123456"
    val prisonId = "HEI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.CONVICTION_STATUS_UPDATED_EVENT_TYPE.value,
      createPrisonerConvictionStatusChangedAdditionalInformationJson(prisonerId = prisonerId, convictedStatus = "Convicted"),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.CONVICTION_STATUS_UPDATED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "OUT"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    verifyNoInteractions(nomisSyncService)
    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(0)
  }
}
