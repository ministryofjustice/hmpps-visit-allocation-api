package uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.PublishEventException
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class VisitAllocationPrisonerRetrySqsService(
  private val hmppsQueueService: HmppsQueueService,
  @param:Qualifier("objectMapper")
  private val objectMapper: ObjectMapper,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val visitAllocationPrisonerRetryQueue by lazy { hmppsQueueService.findByQueueId("prisonvisitsallocationprisonerretryqueue") ?: throw RuntimeException("Queue with name prisonvisitsallocationprisonerretryqueue doesn't exist") }
  private val visitAllocationPrisonerRetrySqsClient by lazy { visitAllocationPrisonerRetryQueue.sqsClient }
  private val visitAllocationPrisonerRetryQueueUrl by lazy { visitAllocationPrisonerRetryQueue.queueUrl }

  fun sendToVisitAllocationPrisonerRetryQueue(allocationJobReference: String, prisonerId: String) {
    log.info("Sending to visit allocation prisoner retry queue - $allocationJobReference, prisonerId - $prisonerId")

    try {
      // drop the message on the visit allocation event job queue
      visitAllocationPrisonerRetrySqsClient.sendMessage(
        buildVisitAllocationPrisonerRetryJobMessage(allocationJobReference, prisonerId),
      )
    } catch (e: Throwable) {
      val message = "Failed to send SQS message with visit allocation reference - $allocationJobReference, prisonerId - $prisonerId"
      log.error(message, e)
      throw PublishEventException(message, e)
    }
    log.info("Successfully sent SQS message with visit allocation reference - $allocationJobReference, prisonerId - $prisonerId")
  }

  private fun buildVisitAllocationPrisonerRetryJobMessage(allocationJobReference: String, prisonerId: String): SendMessageRequest = SendMessageRequest.builder()
    .queueUrl(visitAllocationPrisonerRetryQueueUrl)
    .messageBody(objectMapper.writeValueAsString(VisitAllocationPrisonerRetryJob(allocationJobReference, prisonerId)))
    .build()

  data class VisitAllocationPrisonerRetryJob(
    val jobReference: String,
    val prisonerId: String,
  )
}
