package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerReleasedInfo

@Service
class PrisonerReleasedEventHandler(
  objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val prisonerSearchClient: PrisonerSearchClient,
  private val nomisSyncService: NomisSyncService,
) : DomainEventHandler {

  private val processor = StandardDomainEventHandler(
    objectMapper = objectMapper,
    clazz = PrisonerReleasedInfo::class.java,
    shouldProcess = ::shouldProcess,
    isDpsPrison = ::isDpsPrison,
    processDps = { /* no-op */ },
    processNomis = ::processNomis,
  )

  override fun handle(domainEvent: DomainEvent) {
    processor.handle(domainEvent)
  }

  private fun shouldProcess(info: PrisonerReleasedInfo): Boolean = true

  private fun isDpsPrison(info: PrisonerReleasedInfo): Boolean {
    val prisoner = prisonerSearchClient.getPrisonerById(info.prisonerId)
    return prisonService.getPrisonByCode(prisoner.prisonId)?.active == true
  }

  private fun processNomis(info: PrisonerReleasedInfo) {
    nomisSyncService.syncPrisonerBalanceFromEventChange(info.prisonerId, DomainEventType.PRISONER_RELEASED_EVENT_TYPE)
  }
}
