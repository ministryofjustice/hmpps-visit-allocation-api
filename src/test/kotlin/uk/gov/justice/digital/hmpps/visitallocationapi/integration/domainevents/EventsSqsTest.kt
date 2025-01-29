package uk.gov.justice.digital.hmpps.visitallocationapi.integration.domainevents

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonerIncentivesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.IncentivesMockExtension.Companion.incentivesMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.service.DomainEventListenerService.Companion.CONVICTION_STATUS_UPDATED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

class EventsSqsTest : EventsIntegrationTestBase() {

  @Test
  fun `test prisoner-conviction-status-updated event is processed`() {
    // Given
    val domainEvent = createDomainEventJson(
      CONVICTION_STATUS_UPDATED_EVENT_TYPE,
      createPrisonerConvictionStatusChangedAdditionalInformationJson("TEST", "Convicted"),
    )
    val publishRequest = createDomainEventPublishRequest(CONVICTION_STATUS_UPDATED_EVENT_TYPE, domainEvent)

    // When
    awsSnsClient.publish(publishRequest).get()
    prisonerSearchMockServer.stubGetPrisonerById("TEST", prisoner = PrisonerDto(prisonId = "HEI", prisonerId = "TEST"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory("TEST", prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonIncentiveLevels(prisonId = "HEI", levelCode = "STD", prisonIncentiveAmountsDto = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))

    // Then
    await untilCallTo { sqsClient.countMessagesOnQueue(queueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(prisonerConvictionStatusUpdatedProcessorSpy, times(1)).processEvent(objectMapper.readValue(domainEvent, DomainEvent::class.java)) }
    await untilAsserted { verify(visitOrderRepository, times(1)).saveAll(any<List<VisitOrder>>()) }
  }
}
