package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerReleasedInfo

@Service
class PrisonerReleasedEventHandler(
  objectMapper: ObjectMapper,
  prisonService: PrisonService,
  prisonerSearchClient: PrisonerSearchClient,
) : BaseDomainEventHandler<PrisonerReleasedInfo>(
  objectMapper,
  prisonService,
  prisonerSearchClient,
  PrisonerReleasedInfo::class.java,
) {

  override fun shouldProcess(additionalInfo: PrisonerReleasedInfo): Boolean {
    TODO("Not yet implemented")
  }

  override fun isDpsPrison(additionalInfo: PrisonerReleasedInfo): Boolean = prisonService.getPrisonByCode(additionalInfo.prisonCode)?.active == true

  override fun processDps(additionalInfo: PrisonerReleasedInfo) {
    TODO("Not yet implemented")
  }

  override fun processNomis(additionalInfo: PrisonerReleasedInfo) {
    TODO("Not yet implemented")
  }
}
