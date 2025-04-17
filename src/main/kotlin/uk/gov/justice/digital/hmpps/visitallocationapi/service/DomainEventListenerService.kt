package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers.DomainEventHandlerRegistry

@Service
class DomainEventListenerService(private val handlerRegistry: DomainEventHandlerRegistry) {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }
  suspend fun handleMessage(domainEvent: DomainEvent) {
    val handler = handlerRegistry.getHandler(domainEvent.eventType)

    if (handler != null) {
      handler.handle(domainEvent)
    } else {
      // TODO: Should we throw an error.
      LOG.error("No handler found for event type: {}", domainEvent.eventType)
    }
  }
}
