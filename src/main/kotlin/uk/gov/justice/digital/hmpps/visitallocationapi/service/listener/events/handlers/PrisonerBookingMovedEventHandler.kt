package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerBookingMovedInfo

@Service
class PrisonerBookingMovedEventHandler(
  objectMapper: ObjectMapper,
  prisonService: PrisonService,
  prisonerSearchClient: PrisonerSearchClient,
  nomisSyncService: NomisSyncService,
) : BaseDomainEventHandler<PrisonerBookingMovedInfo>(
  objectMapper,
  prisonService,
  prisonerSearchClient,
  nomisSyncService,
  PrisonerBookingMovedInfo::class.java,
) {

  // TODO: VB-5234 - Is it possible for the prisoners in this event additional information, to be from different prisons? As if yes, it
  //  might change how we process, and might need to override the "handle" method, to process each prisoner differently if one is from NOMIS prison
  //  and other is from DPS prison.
  override fun shouldProcess(additionalInfo: PrisonerBookingMovedInfo): Boolean {
    val movedFromPrisoner = prisonerSearchClient.getPrisonerById(additionalInfo.movedFromNomsNumber)
    val movedToPrisoner = prisonerSearchClient.getPrisonerById(additionalInfo.movedToNomsNumber)
    return (movedFromPrisoner.inOutStatus == "IN" || movedToPrisoner.inOutStatus == "IN")
  }

  override fun isDpsPrison(additionalInfo: PrisonerBookingMovedInfo): Boolean {
    val prisoner = prisonerSearchClient.getPrisonerById(additionalInfo.movedFromNomsNumber)
    return prisonService.getPrisonByCode(prisoner.prisonId)?.active == true
  }

  override fun processDps(additionalInfo: PrisonerBookingMovedInfo) {
    TODO("Not yet implemented")
  }

  override fun processNomis(additionalInfo: PrisonerBookingMovedInfo) {
    nomisSyncService.syncPrisonerBalanceFromEventChange(additionalInfo.movedFromNomsNumber)
    nomisSyncService.syncPrisonerBalanceFromEventChange(additionalInfo.movedToNomsNumber)
  }
}
