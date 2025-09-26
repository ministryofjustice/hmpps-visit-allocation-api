package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener

import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
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

  private val tracer = GlobalOpenTelemetry.getTracer("uk.gov.justice.digital.hmpps.visitallocationapi.service.listener")

  @SqsListener(PRISON_VISITS_ALLOCATION_PRISONER_RETRY_QUEUE_CONFIG_KEY, factory = "hmppsQueueContainerFactoryProxy", maxConcurrentMessages = "2", maxMessagesPerPoll = "2")
  fun processMessage(visitAllocationPrisonerRetryJob: VisitAllocationPrisonerRetryJob): CompletableFuture<Void?> = CoroutineScope(Context.root().asContextElement()).future {

    val span = tracer.spanBuilder("VisitAllocationDomainEventProcessingJob")
      .setSpanKind(SpanKind.CONSUMER)
      .setNoParent() // Force a new trace ID here to stop all jobs being processed under the same operation_id
      .startSpan()

    val scope = span.makeCurrent()

    try {
      log.debug("Processing prisoner on the visits allocation prisoner retry queue - {}", visitAllocationPrisonerRetryJob)
      prisonerRetryService.handlePrisonerRetry(visitAllocationPrisonerRetryJob.jobReference, visitAllocationPrisonerRetryJob.prisonerId)
    } catch (t: Throwable) {
      span.recordException(t)
      throw t
    } finally {
      scope.close()
      span.end()
    }

    null
  }
}
