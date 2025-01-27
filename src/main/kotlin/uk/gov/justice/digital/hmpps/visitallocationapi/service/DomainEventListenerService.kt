package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.processors.PrisonerConvictionStatusUpdatedProcessor

@Service
class DomainEventListenerService(
  val prisonerConvictionStatusUpdatedProcessor: PrisonerConvictionStatusUpdatedProcessor,
) {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val CONVICTION_STATUS_UPDATED_EVENT_TYPE = "prisoner-offender-search.prisoner.conviction-status.updated"
  }

  suspend fun handleMessage(domainEvent: DomainEvent) {
    LOG.info("received event: {}", domainEvent)

    when (domainEvent.eventType) {
      CONVICTION_STATUS_UPDATED_EVENT_TYPE -> prisonerConvictionStatusUpdatedProcessor.processEvent(domainEvent)
      else -> LOG.info("invalid message type: {}", domainEvent)
    }
  }
}
