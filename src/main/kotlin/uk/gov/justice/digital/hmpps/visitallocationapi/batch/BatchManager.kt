package uk.gov.justice.digital.hmpps.visitallocationapi.batch

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

enum class BatchType {
  RETRY_EVENTS_DLQ,
}

@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
@Service
class BatchManager(
  @param:Value("\${batch.type}") private val batchType: BatchType,
  private val retryEventsDlqService: RetryEventsDlqService,
) {
  companion object {
    private val LOG = LoggerFactory.getLogger(this::class.java)
  }

  @EventListener
  fun onApplicationEvent(event: ContextRefreshedEvent) = runBatchJob(batchType)
    .also { event.closeApplication() }

  fun runBatchJob(batchType: BatchType) = runBlocking {
    LOG.info("Running batch job type {}", batchType)
    when (batchType) {
      BatchType.RETRY_EVENTS_DLQ -> retryEventsDlqService.retryEventsDlqMessages()
    }
    LOG.info("Finished batch job type {}", batchType)
  }

  private fun ContextRefreshedEvent.closeApplication() = (this.applicationContext as ConfigurableApplicationContext).close()
}
