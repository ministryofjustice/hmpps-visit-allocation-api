package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent

@Service
class DomainEventListenerService {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val CONVICTION_STATUS_CHANGED_EVENT_TYPE = "prisoner-offender-search.prisoner.conviction-status.updated"
  }

  suspend fun handleMessage(domainEvent: DomainEvent) {
    log.info("received event: {}", domainEvent)

    when (domainEvent.eventType) {
      CONVICTION_STATUS_CHANGED_EVENT_TYPE -> log.info("received conviction status changed event: {}", domainEvent)
      else -> log.info("invalid message type: {}", domainEvent)
    }
  }
}
