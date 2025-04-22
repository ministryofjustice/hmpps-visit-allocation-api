package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerBookingMovedInfo

@Service
class PrisonerBookingMovedEventHandler(
  private val objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val prisonerSearchClient: PrisonerSearchClient,
  private val nomisSyncService: NomisSyncService,
) : DomainEventHandler {

  override fun handle(domainEvent: DomainEvent) {
    val additionalInfo = objectMapper.readValue(domainEvent.additionalInformation, PrisonerBookingMovedInfo::class.java)

    val movedFromPrisoner = prisonerSearchClient.getPrisonerById(additionalInfo.movedFromNomsNumber)
    if (prisonService.getPrisonByCode(movedFromPrisoner.prisonId)?.active == true) {
      processDps(movedFromPrisoner.prisonerId)
    } else {
      processNomis(movedFromPrisoner.prisonerId)
    }

    val movedToNomsNumber = prisonerSearchClient.getPrisonerById(additionalInfo.movedToNomsNumber)
    if (prisonService.getPrisonByCode(movedToNomsNumber.prisonId)?.active == true) {
      processDps(movedToNomsNumber.prisonerId)
    } else {
      processNomis(movedToNomsNumber.prisonerId)
    }
  }

  private fun processDps(prisonerId: String) {
    TODO("processDps not yet implemented")
  }

  private fun processNomis(prisonerId: String) {
    nomisSyncService.syncPrisonerBalanceFromEventChange(prisonerId)
  }
}
