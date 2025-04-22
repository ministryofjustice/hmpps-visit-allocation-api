package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerReceivedInfo

@Service
class PrisonerReceivedEventHandler(
  objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val nomisSyncService: NomisSyncService,
) : DomainEventHandler {

  private val processor = StandardDomainEventHandler(
    objectMapper = objectMapper,
    clazz = PrisonerReceivedInfo::class.java,
    shouldProcess = ::shouldProcess,
    isDpsPrison = ::isDpsPrison,
    processDps = ::processDps,
    processNomis = ::processNomis,
  )

  override fun handle(domainEvent: DomainEvent) {
    processor.handle(domainEvent)
  }

  private fun shouldProcess(info: PrisonerReceivedInfo): Boolean {
    // TODO: VB-5234 - Filter by receive type reasons (e.g. ADMISSION, RETURN_FROM_COURT)
    return true
  }

  private fun isDpsPrison(info: PrisonerReceivedInfo): Boolean = prisonService.getPrisonByCode(info.prisonCode)?.active == true

  private fun processDps(info: PrisonerReceivedInfo) {
    TODO("processDps not yet implemented")
  }

  private fun processNomis(info: PrisonerReceivedInfo) {
    nomisSyncService.syncPrisonerBalanceFromEventChange(info.prisonerId)
  }
}
