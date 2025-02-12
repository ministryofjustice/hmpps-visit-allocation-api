package uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.function.Supplier

@Service
class VisitAllocationEventJobSqsService(
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
  @Value("\${hmpps.sqs.queues.visitsallocationeventjob.queueName}")
  private val queueName: String,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val visitsAllocationEventJobQueue by lazy { hmppsQueueService.findByQueueName(queueName) ?: throw RuntimeException("Queue with name $queueName doesn't exist") }
  private val visitsAllocationEventJobSqsClient by lazy { visitsAllocationEventJobQueue.sqsClient }
  private val visitsAllocationEventJobQueueUrl by lazy { visitsAllocationEventJobQueue.queueUrl }

  fun sendVisitAllocationEventToAllocationJobQueue(prisonCode: String) {
    log.info("Sending visit allocation job with prisonCode - $prisonCode")

    try {
      // drop the message on the visit allocation event job queue
      visitsAllocationEventJobSqsClient.sendMessage(
        buildVisitAllocationEventToAllocationJobMessage(prisonCode),
      )
    } catch (e: Throwable) {
      val message = "Failed to send visit allocation job with prisonCode - $prisonCode"
      log.error(message, e)
      throw PublishEventException(message, e)
    }
    log.info("Successfully sent visit allocation job with prisonCode - $prisonCode")
  }

  private fun buildVisitAllocationEventToAllocationJobMessage(prisonCode: String): SendMessageRequest = SendMessageRequest.builder()
    .queueUrl(visitsAllocationEventJobQueueUrl)
    .messageBody(objectMapper.writeValueAsString(VisitAllocationEventJob(prisonCode)))
    .build()
}

data class VisitAllocationEventJob(
  val prisonCode: String,
)

class PublishEventException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<PublishEventException> {
  override fun get(): PublishEventException = PublishEventException(message, cause)
}
