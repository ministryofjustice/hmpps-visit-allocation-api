package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerReceivedInfo

@Service
class PrisonerReceivedEventHandler(
  objectMapper: ObjectMapper,
  prisonService: PrisonService,
  prisonerSearchClient: PrisonerSearchClient,
) : BaseDomainEventHandler<PrisonerReceivedInfo>(
  objectMapper,
  prisonService,
  prisonerSearchClient,
  PrisonerReceivedInfo::class.java,
) {

  override fun shouldProcess(additionalInfo: PrisonerReceivedInfo): Boolean {
    TODO("Not yet implemented")
  }

  override fun isDpsPrison(additionalInfo: PrisonerReceivedInfo): Boolean = prisonService.getPrisonByCode(additionalInfo.prisonCode)?.active == true

  override fun processDps(additionalInfo: PrisonerReceivedInfo) {
    TODO("Not yet implemented")
  }

  override fun processNomis(additionalInfo: PrisonerReceivedInfo) {
    TODO("Not yet implemented")
  }
}
