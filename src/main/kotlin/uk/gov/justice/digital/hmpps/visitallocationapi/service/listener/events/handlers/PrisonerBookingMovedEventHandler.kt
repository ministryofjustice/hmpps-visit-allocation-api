package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerBookingMovedInfo

@Service
class PrisonerBookingMovedEventHandler(
  objectMapper: ObjectMapper,
  prisonService: PrisonService,
  prisonerSearchClient: PrisonerSearchClient,
) : BaseDomainEventHandler<PrisonerBookingMovedInfo>(
  objectMapper,
  prisonService,
  prisonerSearchClient,
  PrisonerBookingMovedInfo::class.java,
) {

  override fun handle(domainEvent: DomainEvent) {
    TODO("Not yet implemented")
  }

  override fun shouldProcess(additionalInfo: PrisonerBookingMovedInfo): Boolean {
    TODO("Not yet implemented")
  }

  override fun isDpsPrison(additionalInfo: PrisonerBookingMovedInfo): Boolean {
    TODO("Not yet implemented")
  }

  override fun processDps(additionalInfo: PrisonerBookingMovedInfo) {
    TODO("Not yet implemented")
  }

  override fun processNomis(additionalInfo: PrisonerBookingMovedInfo) {
    TODO("Not yet implemented")
  }
}
