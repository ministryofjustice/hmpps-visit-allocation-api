package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerBookingMovedInfo
import java.time.LocalDate

@Service
class PrisonerBookingMovedEventHandler(
  private val objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val prisonerSearchClient: PrisonerSearchClient,
  private val nomisSyncService: NomisSyncService,
  private val prisonerDetailsService: PrisonerDetailsService,
) : DomainEventHandler {

  override fun handle(domainEvent: DomainEvent) {
    val additionalInfo = objectMapper.readValue(domainEvent.additionalInformation, PrisonerBookingMovedInfo::class.java)

    val movedFromPrisoner = prisonerSearchClient.getPrisonerById(additionalInfo.movedFromNomsNumber)
    if (!prisonService.getPrisonEnabledForDpsByCode(movedFromPrisoner.prisonId)) {
      processNomis(movedFromPrisoner.prisonerId)
    }

    val movedToPrisoner = prisonerSearchClient.getPrisonerById(additionalInfo.movedToNomsNumber)
    if (!prisonService.getPrisonEnabledForDpsByCode(movedToPrisoner.prisonId)) {
      processNomis(movedToPrisoner.prisonerId)
    }
  }

  private fun processNomis(prisonerId: String) {
    val dpsPrisoner = prisonerDetailsService.getPrisonerDetails(prisonerId)
    if (dpsPrisoner == null) {
      prisonerDetailsService.createPrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    }

    nomisSyncService.syncPrisonerBalanceFromEventChange(prisonerId, DomainEventType.PRISONER_BOOKING_MOVED_EVENT_TYPE)
  }
}
