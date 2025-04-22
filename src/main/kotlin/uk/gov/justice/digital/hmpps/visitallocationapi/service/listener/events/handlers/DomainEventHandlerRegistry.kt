package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import org.springframework.stereotype.Component

@Component
class DomainEventHandlerRegistry(
  convictionStatusChangedHandler: ConvictionStatusChangedEventHandler,
  prisonerReceivedHandler: PrisonerReceivedEventHandler,
  prisonerReleasedHandler: PrisonerReleasedEventHandler,
  prisonerMergedHandler: PrisonerMergedEventHandler,
  prisonerBookingMovedHandler: PrisonerBookingMovedEventHandler,
) {
  companion object {
    const val CONVICTION_STATUS_UPDATED_EVENT_TYPE = "prisoner-offender-search.prisoner.convicted-status-changed"
    const val PRISONER_RECEIVED_EVENT_TYPE = "prison-offender-events.prisoner.received"
    const val PRISONER_RELEASED_EVENT_TYPE = "prison-offender-events.prisoner.released"
    const val PRISONER_MERGED_EVENT_TYPE = "prison-offender-events.prisoner.merged"
    const val PRISONER_BOOKING_MOVED_EVENT_TYPE = "prison-offender-events.prisoner.booking.moved"
  }

  private val handlers: Map<String, DomainEventHandler> = mapOf(
    CONVICTION_STATUS_UPDATED_EVENT_TYPE to convictionStatusChangedHandler,
    PRISONER_RECEIVED_EVENT_TYPE to prisonerReceivedHandler,
    PRISONER_RELEASED_EVENT_TYPE to prisonerReleasedHandler,
    PRISONER_MERGED_EVENT_TYPE to prisonerMergedHandler,
    PRISONER_BOOKING_MOVED_EVENT_TYPE to prisonerBookingMovedHandler,
  )

  fun getHandler(eventType: String): DomainEventHandler? = handlers[eventType]
}
