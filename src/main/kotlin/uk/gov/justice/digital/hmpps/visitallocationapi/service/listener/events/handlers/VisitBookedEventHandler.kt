package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.VisitSchedulerClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.VisitBookedInfo

@Service
class VisitBookedEventHandler(
  private val objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val visitSchedulerClient: VisitSchedulerClient,
) : DomainEventHandler {

  override fun handle(domainEvent: DomainEvent) {
    val additionalInfo = objectMapper.readValue(domainEvent.additionalInformation, VisitBookedInfo::class.java)

    val visit = visitSchedulerClient.getVisitByReference(additionalInfo.reference)

    if (prisonService.getPrisonEnabledForDpsByCode(visit.prisonCode)) {
      TODO("Add main logic")
    }
  }
}
