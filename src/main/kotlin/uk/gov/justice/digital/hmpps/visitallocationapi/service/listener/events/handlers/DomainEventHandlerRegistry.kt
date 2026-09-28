package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType.PRISONER_MERGED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType.PRISONER_RECEIVED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType.VISIT_BOOKED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType.VISIT_CANCELLED_EVENT_TYPE

@Component
class DomainEventHandlerRegistry(
  prisonerReceivedHandler: PrisonerReceivedEventHandler,
  prisonerMergedHandler: PrisonerMergedEventHandler,
  visitBookedEventHandler: VisitBookedEventHandler,
  visitCancelledEventHandler: VisitCancelledEventHandler,
) {
  private val handlers: Map<String, DomainEventHandler> = mapOf(
    PRISONER_RECEIVED_EVENT_TYPE.value to prisonerReceivedHandler,
    PRISONER_MERGED_EVENT_TYPE.value to prisonerMergedHandler,
    VISIT_BOOKED_EVENT_TYPE.value to visitBookedEventHandler,
    VISIT_CANCELLED_EVENT_TYPE.value to visitCancelledEventHandler,
  )

  fun getHandler(eventType: String): DomainEventHandler? = handlers[eventType]
}
