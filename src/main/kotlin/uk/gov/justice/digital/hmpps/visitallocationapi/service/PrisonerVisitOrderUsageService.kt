package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.SessionTemplateVisitOrderRestrictionType
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.VisitDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate
import java.util.UUID

@Transactional
@Service
class PrisonerVisitOrderUsageService(
  private val prisonerDetailsService: PrisonerDetailsService,
  private val changeLogService: ChangeLogService,
  private val telemetryClientService: TelemetryClientService,
  private val visitOrderHistoryService: VisitOrderHistoryService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processPrisonerVisitOrderUsage(visit: VisitDto, visitOrderRestriction: SessionTemplateVisitOrderRestrictionType? = null): UUID? {
    val dpsPrisonerDetails = prisonerDetailsService.getPrisonerDetailsWithLock(visit.prisonerId)
      ?: prisonerDetailsService.createPrisonerDetails(visit.prisonerId, LocalDate.now().minusDays(14), null)

    // Due to our SQS queues being "At least once delivery", this specific event needs to return early if this visit has already been mapped.
    if (visitAlreadyMapped(dpsPrisonerDetails, visit, visitOrderRestriction)) {
      LOG.info("Duplicate request to map a visit booking (${visit.reference}) to a visit order for prisoner ${dpsPrisonerDetails.prisonerId}. Exiting early.")
      return null
    }

    if (visitOrderRestriction == SessionTemplateVisitOrderRestrictionType.NONE) {
      LOG.info("Visit booking (${visit.reference}) does not require a visit order for prisoner ${dpsPrisonerDetails.prisonerId}. Logging history only.")
      visitOrderHistoryService.logAllocationUsedByVisit(dpsPrisonerDetails, visit.reference, "NONE")
      return null
    }

    // Find the oldest PVO to use. If none exists, find the oldest VO to use.
    val selected: VisitOrder? = dpsPrisonerDetails.visitOrders
      .asSequence()
      .filter { it.type == VisitOrderType.PVO }
      .filter { it.status == VisitOrderStatus.AVAILABLE }
      .minByOrNull { it.createdTimestamp }
      ?: dpsPrisonerDetails.visitOrders
        .asSequence()
        .filter { it.type == VisitOrderType.VO }
        .filter { it.status in listOf(VisitOrderStatus.AVAILABLE, VisitOrderStatus.ACCUMULATED) }
        .minByOrNull { it.createdTimestamp }

    if (selected != null) {
      selected.status = VisitOrderStatus.USED
      selected.visitReference = visit.reference
    } else {
      // If none are found, generate a negative VO and save to prisoners negativeVisitOrders list.
      val negativeVo = NegativeVisitOrder(
        status = NegativeVisitOrderStatus.USED,
        type = VisitOrderType.VO,
        prisoner = dpsPrisonerDetails,
        visitReference = visit.reference,
      )
      dpsPrisonerDetails.negativeVisitOrders.add(negativeVo)
    }

    visitOrderHistoryService.logAllocationUsedByVisit(dpsPrisonerDetails, visit.reference, selected?.type?.name ?: VisitOrderType.VO.name)
    val changeLog = changeLogService.createLogAllocationUsedByVisit(dpsPrisonerDetails, visit.reference)
    dpsPrisonerDetails.changeLogs.add(changeLog)

    telemetryClientService.trackEvent(
      TelemetryEventType.VO_CONSUMED_BY_VISIT,
      mapOf(
        "visitReference" to visit.reference,
        "prisonerId" to visit.prisonerId,
        "voType" to (selected?.type?.name ?: "vo"),
      ),
    )

    return changeLog.reference
  }

  private fun visitAlreadyMapped(
    dpsPrisonerDetails: PrisonerDetails,
    visit: VisitDto,
    visitOrderRestriction: SessionTemplateVisitOrderRestrictionType?,
  ): Boolean = dpsPrisonerDetails.visitOrders.any { it.visitReference == visit.reference } ||
    dpsPrisonerDetails.negativeVisitOrders.any { it.visitReference == visit.reference } ||
    (
      visitOrderRestriction == SessionTemplateVisitOrderRestrictionType.NONE &&
        visitOrderHistoryService.allocationUsedByVisitExists(dpsPrisonerDetails.prisonerId, visit.reference)
      )
}
