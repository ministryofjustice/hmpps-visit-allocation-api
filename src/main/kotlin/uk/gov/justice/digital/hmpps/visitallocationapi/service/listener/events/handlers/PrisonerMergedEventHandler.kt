package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerMergedInfo

@Service
class PrisonerMergedEventHandler(
  objectMapper: ObjectMapper,
  prisonService: PrisonService,
  prisonerSearchClient: PrisonerSearchClient,
) : BaseDomainEventHandler<PrisonerMergedInfo>(
  objectMapper,
  prisonService,
  prisonerSearchClient,
  PrisonerMergedInfo::class.java,
) {

  override fun handle(domainEvent: DomainEvent) {
    TODO("Not yet implemented")
  }

  override fun shouldProcess(additionalInfo: PrisonerMergedInfo): Boolean {
    TODO("Not yet implemented")
  }

  override fun isDpsPrison(additionalInfo: PrisonerMergedInfo): Boolean {
    TODO("Not yet implemented")
  }

  override fun processDps(additionalInfo: PrisonerMergedInfo) {
    TODO("Not yet implemented")
  }

  override fun processNomis(additionalInfo: PrisonerMergedInfo) {
    TODO("Not yet implemented")
  }
}
