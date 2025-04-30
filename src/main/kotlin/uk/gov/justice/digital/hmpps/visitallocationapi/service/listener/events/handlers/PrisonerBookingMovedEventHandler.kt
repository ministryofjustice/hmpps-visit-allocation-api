package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
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
    val movedFromPrisonerPrison = prisonService.getPrisonByCode(movedFromPrisoner.prisonId)
    if (movedFromPrisonerPrison == null || !movedFromPrisonerPrison.active) {
      processNomis(movedFromPrisoner.prisonerId)
    }

    val movedToPrisoner = prisonerSearchClient.getPrisonerById(additionalInfo.movedToNomsNumber)
    val movedToPrisonerPrison = prisonService.getPrisonByCode(movedFromPrisoner.prisonId)
    if (movedToPrisonerPrison == null || !movedToPrisonerPrison.active) {
      processNomis(movedToPrisoner.prisonerId)
    }
  }

  private fun processNomis(prisonerId: String) {
    nomisSyncService.syncPrisonerBalanceFromEventChange(prisonerId, DomainEventType.PRISONER_BOOKING_MOVED_EVENT_TYPE)
  }
}
