package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerMergedInfo

@Service
class PrisonerMergedEventHandler(
  objectMapper: ObjectMapper,
  prisonService: PrisonService,
  prisonerSearchClient: PrisonerSearchClient,
  nomisSyncService: NomisSyncService,
) : BaseDomainEventHandler<PrisonerMergedInfo>(
  objectMapper,
  prisonService,
  prisonerSearchClient,
  nomisSyncService,
  PrisonerMergedInfo::class.java,
) {

  override fun shouldProcess(additionalInfo: PrisonerMergedInfo): Boolean = true

  override fun isDpsPrison(additionalInfo: PrisonerMergedInfo): Boolean {
    val prisoner = prisonerSearchClient.getPrisonerById(additionalInfo.prisonerId)
    return prisonService.getPrisonByCode(prisoner.prisonId)?.active == true
  }

  override fun processDps(additionalInfo: PrisonerMergedInfo) {
    TODO("Not yet implemented")
  }

  override fun processNomis(additionalInfo: PrisonerMergedInfo) {
    nomisSyncService.syncPrisonerBalanceFromEventChange(additionalInfo.prisonerId)
    nomisSyncService.syncPrisonerRemoved(additionalInfo.removedPrisonerId)
  }
}
