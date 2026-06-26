package uk.gov.justice.digital.hmpps.visitallocationapi.batch

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener.Companion.PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.RetryDlqRequest
import uk.gov.justice.hmpps.sqs.RetryDlqResult

@Service
class RetryEventsDlqService(
  private val hmppsQueueService: HmppsQueueService,
) {
  companion object {
    private val LOG = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun retryEventsDlqMessages(): RetryDlqResult {
    val queue = hmppsQueueService.findByQueueId(PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY)
      ?: throw IllegalArgumentException("Queue $PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY not found")

    return hmppsQueueService.retryDlqMessages(RetryDlqRequest(queue))
      .also { LOG.info("Retried {} messages from events DLQ", it.messagesFoundCount) }
  }
}
