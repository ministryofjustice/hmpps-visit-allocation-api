package uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class VisitAllocationPrisonerRetrySqsService(
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
  @Value("\${hmpps.sqs.queues.prisonvisitsallocationprisonerretryqueue.queueName}")
  private val queueName: String,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val visitAllocationPrisonerRetryQueue by lazy { hmppsQueueService.findByQueueName(queueName) ?: throw RuntimeException("Queue with name $queueName doesn't exist") }
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
