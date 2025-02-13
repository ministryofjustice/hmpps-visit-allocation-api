package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener

import io.awspring.cloud.sqs.annotation.SqsListener
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

  @SqsListener(PRISON_VISITS_ALLOCATION_EVENT_JOB_QUEUE_CONFIG_KEY, factory = "hmppsQueueContainerFactoryProxy")
  fun processMessage(visitAllocationEventJob: VisitAllocationEventJob): CompletableFuture<Void?> = CoroutineScope(Context.current().asContextElement()).future {
    visitAllocationByPrisonJobListenerService.handleVisitAllocationJob(visitAllocationEventJob)
    null
  }
}
