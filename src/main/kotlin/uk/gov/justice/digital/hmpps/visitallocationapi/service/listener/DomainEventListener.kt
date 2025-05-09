package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.DomainEventListenerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.SQSMessage
import java.util.concurrent.CompletableFuture

@Service
class DomainEventListener(
  private val domainEventListenerService: DomainEventListenerService,
  private val objectMapper: ObjectMapper,
  @Value("\${domain-event-processing.enabled}") val domainEventProcessingEnabled: Boolean,
) {
  companion object {
    const val PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY = "prisonvisitsallocationevents"
    private val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY, factory = "hmppsQueueContainerFactoryProxy")
  fun processMessage(sqsMessage: SQSMessage): CompletableFuture<Void?> {
    if (domainEventProcessingEnabled) {
      val event = objectMapper.readValue(sqsMessage.message, DomainEvent::class.java)
      return CoroutineScope(Context.current().asContextElement()).future {
        domainEventListenerService.handleMessage(event)
        null
      }
    } else {
      LOG.debug("Domain event processing is disabled")
      return CompletableFuture.completedFuture(null)
    }
  }
}
