package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.DomainEventListenerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.SQSMessage

@Service
class DomainEventListener(
  private val domainEventListenerService: DomainEventListenerService,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    const val PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY = "prisonvisitsallocationevents"
    private val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY, factory = "hmppsQueueContainerFactoryProxy")
  fun processMessage(sqsMessage: SQSMessage) {
    val event = objectMapper.readValue(sqsMessage.message, DomainEvent::class.java)

    var dLQException: Exception? = null
    try {
      CoroutineScope(Context.current().asContextElement()).future {
        domainEventListenerService.handleMessage(event)
        null
      }
    } catch (e: Exception) {
      LOG.error("Encountered exception while processing event - $e")
      dLQException = e
    }

    // Re-throwing to put message back onto event queue.
    if (dLQException != null) {
      throw dLQException
    }
  }
}
