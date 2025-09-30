package uk.gov.justice.digital.hmpps.visitallocationapi.integration.events

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.test.context.TestPropertySource
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.IncentivesMockExtension.Companion.incentivesMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJob
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

@TestPropertySource(properties = ["feature.events.sns.enabled=true"])
class SnsEnabledEventsSentTest : EventsIntegrationTestBase() {

  @Test
  fun `prison visit allocation batch jobs runs - event sent`() {
    // Given
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE, currentIncentiveLevel = "STD"))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner2.prisonerId, createPrisonerDto(prisonerId = prisoner2.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE, currentIncentiveLevel = "ENH"))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner3.prisonerId, createPrisonerDto(prisonerId = prisoner3.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE, currentIncentiveLevel = "ENH2"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    // Then
    await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any()) }

    // And
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    verify(telemetryClient, times(3)).trackEvent(any(), any(), eq(null))
  }
}
