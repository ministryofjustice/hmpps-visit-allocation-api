package uk.gov.justice.digital.hmpps.visitallocationapi.integration.events

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
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

@DisplayName("Test for Domain Event Listener disabled")
@TestPropertySource(properties = ["domain-event-processing.enabled=false"])
class DomainEventsSqsDisabledTest : EventsIntegrationTestBase() {

  @Test
  fun `when domain event processing is set to disabled no domain events are processed`() {
    // Given
    val domainEvent = createDomainEventJson(
      DomainEventType.CONVICTION_STATUS_UPDATED_EVENT_TYPE.value,
      createPrisonerConvictionStatusChangedAdditionalInformationJson("TEST", "Convicted"),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.CONVICTION_STATUS_UPDATED_EVENT_TYPE.value, domainEvent)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verifyNoInteractions(domainEventListenerServiceSpy) }
  }
}
