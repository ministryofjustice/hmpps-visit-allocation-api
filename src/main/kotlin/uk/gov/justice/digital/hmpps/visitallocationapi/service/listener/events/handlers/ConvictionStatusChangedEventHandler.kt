package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerConvictionStatusChangedInfo

@Service
class ConvictionStatusChangedEventHandler(
  objectMapper: ObjectMapper,
  prisonService: PrisonService,
  prisonerSearchClient: PrisonerSearchClient,
  nomisSyncService: NomisSyncService,
) : BaseDomainEventHandler<PrisonerConvictionStatusChangedInfo>(
  objectMapper,
  prisonService,
  prisonerSearchClient,
  nomisSyncService,
  PrisonerConvictionStatusChangedInfo::class.java,
) {

  override fun shouldProcess(additionalInfo: PrisonerConvictionStatusChangedInfo): Boolean {
    val prisoner = prisonerSearchClient.getPrisonerById(additionalInfo.prisonerId)
    return prisoner.inOutStatus == "IN"
  }

  override fun isDpsPrison(additionalInfo: PrisonerConvictionStatusChangedInfo): Boolean {
    val prisoner = prisonerSearchClient.getPrisonerById(additionalInfo.prisonerId)
    return prisonService.getPrisonByCode(prisoner.prisonId)?.active == true
  }

  override fun processDps(additionalInfo: PrisonerConvictionStatusChangedInfo) {
    TODO("Not yet implemented")
  }

  override fun processNomis(additionalInfo: PrisonerConvictionStatusChangedInfo) = nomisSyncService.syncPrisonerBalanceFromEventChange(additionalInfo.prisonerId)
}
