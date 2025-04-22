package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent

class StandardDomainEventHandler<T : Any>(
  private val objectMapper: ObjectMapper,
  private val clazz: Class<T>,
  private val shouldProcess: (T) -> Boolean,
  private val isDpsPrison: (T) -> Boolean,
  private val processDps: (T) -> Unit,
  private val processNomis: (T) -> Unit,
) {
  fun handle(domainEvent: DomainEvent) {
    val additionalInfo = objectMapper.readValue(domainEvent.additionalInformation, clazz)

    if (shouldProcess(additionalInfo)) {
      if (isDpsPrison(additionalInfo)) {
        processDps(additionalInfo)
      } else {
        processNomis(additionalInfo)
      }
    }
  }
}
