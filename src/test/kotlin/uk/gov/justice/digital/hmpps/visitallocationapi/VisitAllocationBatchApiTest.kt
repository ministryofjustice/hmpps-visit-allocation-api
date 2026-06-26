package uk.gov.justice.digital.hmpps.visitallocationapi

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.visitallocationapi.batch.BatchManager
import uk.gov.justice.digital.hmpps.visitallocationapi.batch.RetryEventsDlqService
import uk.gov.justice.digital.hmpps.visitallocationapi.batch.SqsListenerSuppressor
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener.Companion.PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.RetryDlqResult
import jakarta.persistence.EntityManagerFactory
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class VisitAllocationBatchApiTest {
  private val contextRunner = ApplicationContextRunner({ NonClosingAnnotationConfigApplicationContext() })
    .withUserConfiguration(VisitAllocationBatchApi::class.java)
    .withBean(HmppsQueueService::class.java, { hmppsQueueService() })
    .withPropertyValues(
      "batch.enabled=true",
      "batch.type=RETRY_EVENTS_DLQ",
      "hmpps.sqs.enabled=false",
      "spring.autoconfigure.exclude=uk.gov.justice.hmpps.sqs.HmppsSqsConfiguration",
    )

  @Test
  fun `batch context is slimmed down to batch application beans`() {
    contextRunner.run { context ->
      val sourceContext = context.getSourceApplicationContext(NonClosingAnnotationConfigApplicationContext::class.java)

      try {
        assertThat(context).hasSingleBean(BatchManager::class.java)
        assertThat(context).hasSingleBean(RetryEventsDlqService::class.java)
        assertThat(context).hasSingleBean(SqsListenerSuppressor::class.java)
        assertThat(context).doesNotHaveBean(DataSource::class.java)
        assertThat(context).doesNotHaveBean(PlatformTransactionManager::class.java)
        assertThat(context).doesNotHaveBean(EntityManagerFactory::class.java)
        assertThat(context).doesNotHaveBean(Flyway::class.java)
        assertThat(context).doesNotHaveBean(PrisonerDetailsRepository::class.java)

        val visitAllocationBeans = context.beanDefinitionNames
          .mapNotNull { beanName -> context.getType(beanName) }
          .filter { beanClass -> beanClass.packageName.startsWith("uk.gov.justice.digital.hmpps.visitallocationapi") }

        assertThat(visitAllocationBeans)
          .allSatisfy { beanClass ->
            assertThat(beanClass.packageName)
              .isIn(
                "uk.gov.justice.digital.hmpps.visitallocationapi",
                "uk.gov.justice.digital.hmpps.visitallocationapi.batch",
              )
          }
      } finally {
        sourceContext.closeForReal()
      }
    }
  }

  private fun hmppsQueueService(): HmppsQueueService {
    val hmppsQueueService = mock<HmppsQueueService>()
    val hmppsQueue = HmppsQueue(
      id = PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY,
      sqsClient = mock<SqsAsyncClient>(),
      queueName = "prison-visits-allocation-events",
    )

    whenever(hmppsQueueService.findByQueueId(PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY)).thenReturn(hmppsQueue)
    runBlocking {
      whenever(hmppsQueueService.retryDlqMessages(any())).thenReturn(RetryDlqResult(messagesFoundCount = 0))
    }

    return hmppsQueueService
  }

  private class NonClosingAnnotationConfigApplicationContext : AnnotationConfigApplicationContext() {
    override fun close() = Unit

    fun closeForReal() = super.close()
  }
}
