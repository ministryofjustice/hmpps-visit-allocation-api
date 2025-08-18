package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.VisitSchedulerClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ProcessPrisonerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.SnsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.VisitCancelledInfo

@Service
class VisitCancelledEventHandler(
  private val objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val visitSchedulerClient: VisitSchedulerClient,
  private val processPrisonerService: ProcessPrisonerService,
  private val snsService: SnsService,
  private val changeLogService: ChangeLogService,
) : DomainEventHandler {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun handle(domainEvent: DomainEvent) {
    LOG.info("VisitCancelledEventHandler called for event prison.visit-cancelled")
    val additionalInfo = objectMapper.readValue(domainEvent.additionalInformation, VisitCancelledInfo::class.java)

    LOG.info("Getting visit using reference - ${additionalInfo.reference}")
    val visit = visitSchedulerClient.getVisitByReference(additionalInfo.reference)

    if (prisonService.getPrisonEnabledForDpsByCode(visit.prisonCode)) {
      LOG.info("Prison ${visit.prisonCode} is enabled for DPS, processing event")

      val changeLogReference = processPrisonerService.processPrisonerVisitOrderRefund(visit)

      if (changeLogReference != null) {
        val changeLog = changeLogService.findChangeLogForPrisonerByReference(visit.prisonerId, changeLogReference)
        if (changeLog != null) {
          snsService.sendPrisonAllocationAdjustmentCreatedEvent(changeLog)
        }
      }
    } else {
      LOG.info("Prison ${visit.prisonCode} is not enabled for DPS, skipping processing")
    }
  }
}
