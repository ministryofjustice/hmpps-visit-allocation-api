package uk.gov.justice.digital.hmpps.visitallocationapi.enums

@Suppress("unused")
enum class DomainEventType(val value: String) {
  CONVICTION_STATUS_UPDATED_EVENT_TYPE("prisoner-offender-search.prisoner.convicted-status-changed"),
  PRISONER_RECEIVED_EVENT_TYPE("prison-offender-events.prisoner.received"),
  PRISONER_RELEASED_EVENT_TYPE("prison-offender-events.prisoner.released"),
  PRISONER_MERGED_EVENT_TYPE("prison-offender-events.prisoner.merged"),
  PRISONER_BOOKING_MOVED_EVENT_TYPE("prison-offender-events.prisoner.booking.moved"),
}
