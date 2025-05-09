package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener

import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerRetryService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationPrisonerRetrySqsService.VisitAllocationPrisonerRetryJob
import java.util.concurrent.CompletableFuture

@Service
class VisitAllocationPrisonerRetryQueueListener(private val prisonerRetryService: PrisonerRetryService) {

  companion object {
    const val PRISON_VISITS_ALLOCATION_PRISONER_RETRY_QUEUE_CONFIG_KEY = "prisonvisitsallocationprisonerretryqueue"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(PRISON_VISITS_ALLOCATION_PRISONER_RETRY_QUEUE_CONFIG_KEY, factory = "hmppsQueueContainerFactoryProxy")
  fun processMessage(visitAllocationPrisonerRetryJob: VisitAllocationPrisonerRetryJob): CompletableFuture<Void?> = CoroutineScope(Context.current().asContextElement()).future {
    log.debug("Processing prisoner on the visits allocation prisoner retry queue - {}", visitAllocationPrisonerRetryJob)
    prisonerRetryService.handlePrisonerRetry(visitAllocationPrisonerRetryJob.jobReference, visitAllocationPrisonerRetryJob.prisonerId)
    null
  }
}
