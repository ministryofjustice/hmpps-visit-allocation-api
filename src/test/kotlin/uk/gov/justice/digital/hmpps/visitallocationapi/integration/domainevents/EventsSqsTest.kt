package uk.gov.justice.digital.hmpps.visitallocationapi.integration.domainevents

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.visitallocationapi.service.DomainEventListenerService.Companion.CONVICTION_STATUS_CHANGED_EVENT_TYPE
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

class EventsSqsTest : EventsIntegrationTestBase() {

  @Test
  fun `test sqs message is processed`() {
    // Given
    val publishRequest = createDomainEventPublishRequest(CONVICTION_STATUS_CHANGED_EVENT_TYPE)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { sqsClient.countMessagesOnQueue(queueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
  }
}
