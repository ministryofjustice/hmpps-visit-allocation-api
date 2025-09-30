package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener

import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitAllocationByPrisonJobListenerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJob
import java.util.concurrent.CompletableFuture

@Service
class VisitAllocationByPrisonJobListener(
  private val visitAllocationByPrisonJobListenerService: VisitAllocationByPrisonJobListenerService,
) {
  companion object {
    const val PRISON_VISITS_ALLOCATION_EVENT_JOB_QUEUE_CONFIG_KEY = "visitsallocationeventjob"
  }

  private val tracer = GlobalOpenTelemetry.getTracer("uk.gov.justice.digital.hmpps.visitallocationapi.service.listener")

  @SqsListener(PRISON_VISITS_ALLOCATION_EVENT_JOB_QUEUE_CONFIG_KEY, factory = "hmppsQueueContainerFactoryProxy", maxConcurrentMessages = "1", maxMessagesPerPoll = "1")
  fun processMessage(visitAllocationEventJob: VisitAllocationEventJob): CompletableFuture<Void?> = CoroutineScope(Context.root().asContextElement()).future {
    val span = tracer.spanBuilder("VisitAllocationPrisonAllocationJob")
      .setSpanKind(SpanKind.CONSUMER)
      .setNoParent() // Force a new trace ID here to stop all jobs being processed under the same operation_id
      .startSpan()

    val scope = span.makeCurrent()

    try {
      visitAllocationByPrisonJobListenerService.handleVisitAllocationJob(visitAllocationEventJob)
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
