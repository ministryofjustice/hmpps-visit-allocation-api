package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.DomainEventListenerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.SQSMessage
import java.util.concurrent.CompletableFuture

@Service
class DomainEventListener(
  private val domainEventListenerService: DomainEventListenerService,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    const val PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY = "prisonvisitsallocationevents"
  }

  @SqsListener(PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY, factory = "hmppsQueueContainerFactoryProxy")
  fun processMessage(sqsMessage: SQSMessage): CompletableFuture<Void?> {
    val event = objectMapper.readValue(sqsMessage.message, DomainEvent::class.java)
    return CoroutineScope(Context.current().asContextElement()).future {
      domainEventListenerService.handleMessage(event)
      null
    }
  }
}
