package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ProcessPrisonerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.SnsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerMergedInfo
import java.time.LocalDate

@Service
class PrisonerMergedEventHandler(
  objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val prisonerSearchClient: PrisonerSearchClient,
  private val nomisSyncService: NomisSyncService,
  private val processPrisonerService: ProcessPrisonerService,
  private val snsService: SnsService,
  private val changeLogService: ChangeLogService,
  private val prisonerDetailsService: PrisonerDetailsService,
) : DomainEventHandler {

  companion object {
    private val LOG = LoggerFactory.getLogger(this::class.java)
  }

  private val processor = StandardDomainEventHandler(
    objectMapper = objectMapper,
    clazz = PrisonerMergedInfo::class.java,
    shouldProcess = ::shouldProcess,
    isDpsPrison = ::isDpsPrison,
    processDps = ::processDps,
    processNomis = ::processNomis,
  )

  override fun handle(domainEvent: DomainEvent) {
    processor.handle(domainEvent)
  }

  private fun shouldProcess(info: PrisonerMergedInfo): Boolean = true

  private fun isDpsPrison(info: PrisonerMergedInfo): Boolean {
    val prisoner = prisonerSearchClient.getPrisonerById(info.prisonerId)
    return prisonService.getPrisonEnabledForDpsByCode(prisoner.prisonId)
  }

  private fun processDps(info: PrisonerMergedInfo) {
    LOG.info("Handling DPS prison merge event - $info")
    val newPrisonerDetails = prisonerDetailsService.getPrisonerDetails(info.prisonerId)
    if (newPrisonerDetails == null) {
      prisonerDetailsService.createPrisonerDetails(info.prisonerId, LocalDate.now().minusDays(14), null)
    }

    val changeLogReference = processPrisonerService.processPrisonerMerge(info.prisonerId, info.removedPrisonerId)
    if (changeLogReference != null) {
      val changeLog = changeLogService.findChangeLogForPrisonerByReference(info.prisonerId, changeLogReference)
      if (changeLog != null) {
        snsService.sendPrisonAllocationAdjustmentCreatedEvent(changeLog)
      }
    } else {
      LOG.info("No change log generated for merge event - $info")
    }
  }

  private fun processNomis(info: PrisonerMergedInfo) {
    val dpsPrisoner = prisonerDetailsService.getPrisonerDetails(info.prisonerId)
    if (dpsPrisoner == null) {
      prisonerDetailsService.createPrisonerDetails(info.prisonerId, LocalDate.now().minusDays(14), null)
    }

    nomisSyncService.syncPrisonerBalanceFromEventChange(info.prisonerId, DomainEventType.PRISONER_MERGED_EVENT_TYPE)
    nomisSyncService.syncPrisonerRemoved(info.removedPrisonerId)
  }
}
