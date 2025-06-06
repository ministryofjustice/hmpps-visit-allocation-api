package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType.NEW_ADMISSION
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType.POST_MERGE_ADMISSION
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType.READMISSION
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType.READMISSION_SWITCH_BOOKING
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ProcessPrisonerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.SnsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerReceivedInfo
import java.time.LocalDate

@Service
class PrisonerReceivedEventHandler(
  objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val nomisSyncService: NomisSyncService,
  private val processPrisonerService: ProcessPrisonerService,
  private val snsService: SnsService,
  private val prisonerDetailsService: PrisonerDetailsService,
  private val changeLogService: ChangeLogService,
) : DomainEventHandler {

  companion object {
    private val LOG = LoggerFactory.getLogger(this::class.java)
  }

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

  private fun shouldProcess(info: PrisonerReceivedInfo): Boolean = true

  private fun isDpsPrison(info: PrisonerReceivedInfo): Boolean {
    if (prisonService.getPrisonEnabledForDpsByCode(info.prisonCode)) {
      LOG.info("Prison ${info.prisonCode} is enabled for DPS, processing event")
      return true
    } else {
      LOG.info("Prison ${info.prisonCode} is disabled for DPS, skipping event")
      return false
    }
  }

  private fun processDps(info: PrisonerReceivedInfo) {
    if (prisonerDetailsService.getPrisonerDetails(info.prisonerId) == null) {
      LOG.info("New Prisoner ${info.prisonerId} received, adding to DB")
      prisonerDetailsService.createPrisonerDetails(info.prisonerId, LocalDate.now().minusDays(14), null)
    } else {
      if (shouldWipePrisonerBalance(info.reason)) {
        LOG.info("Existing prisoner ${info.prisonerId} received for reason ${info.reason}, wiping balance")
        val changeLog = processPrisonerService.processPrisonerReceivedResetBalance(info.prisonerId, info.reason)
        if (changeLog != null) {
          snsService.sendPrisonAllocationAdjustmentCreatedEvent(changeLog)
        }
      } else {
        LOG.info("Existing prisoner ${info.prisonerId} received for reason ${info.reason}, not wiping balance")
      }
    }
  }

  private fun processNomis(info: PrisonerReceivedInfo) {
    nomisSyncService.syncPrisonerBalanceFromEventChange(info.prisonerId, DomainEventType.PRISONER_RECEIVED_EVENT_TYPE)
  }

  private fun shouldWipePrisonerBalance(reason: PrisonerReceivedReasonType): Boolean = reason in listOf(POST_MERGE_ADMISSION, NEW_ADMISSION, READMISSION_SWITCH_BOOKING, READMISSION)
}
