package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent

interface DomainEventHandler {
  fun handle(domainEvent: DomainEvent)
}
