package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.SessionTemplateVisitOrderRestrictionType
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.VisitDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeRepaymentReason
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VisitOrdersUtil
import java.time.LocalDate
import java.util.UUID

@Transactional
@Service
class PrisonerVisitOrderRefundService(
  private val prisonerDetailsService: PrisonerDetailsService,
  private val changeLogService: ChangeLogService,
  private val telemetryClientService: TelemetryClientService,
  private val visitOrderHistoryService: VisitOrderHistoryService,
  private val visitOrdersUtil: VisitOrdersUtil,
  @param:Value("\${max.visit-orders:26}") val maxAccumulatedVisitOrders: Int,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processPrisonerVisitOrderRefund(
    visit: VisitDto,
    visitOrderRestriction: SessionTemplateVisitOrderRestrictionType? = null,
  ): UUID? {
    val dpsPrisonerDetails = prisonerDetailsService.getPrisonerDetailsWithLock(visit.prisonerId)
      ?: prisonerDetailsService.createPrisonerDetails(visit.prisonerId, LocalDate.now().minusDays(14), null)

    if (visitOrderHistoryService.allocationRefundedByVisitCancelledExists(dpsPrisonerDetails.prisonerId, visit.reference)) {
      LOG.info("Duplicate request to refund a visit order for cancelled visit (${visit.reference}) for prisoner ${dpsPrisonerDetails.prisonerId}. Exiting early.")
      return null
    }

    if (visitOrderRestriction == SessionTemplateVisitOrderRestrictionType.NONE) {
      LOG.info("Visit cancellation (${visit.reference}) does not require a visit order refund for prisoner ${visit.prisonerId}. Logging history only.")
      visitOrderHistoryService.logAllocationRefundedByVisitCancelled(dpsPrisonerDetails, visit.reference, "NONE")
      return null
    }

    // Find the VO used by the visit.
    val voUsedForVisit = dpsPrisonerDetails.visitOrders
      .firstOrNull { it.visitReference == visit.reference }
    var visitOrderTypeUsed = voUsedForVisit?.type ?: VisitOrderType.VO

    if (voUsedForVisit != null) {
      if (voUsedForVisit.type == VisitOrderType.VO && hasPrisonerReachedVoCap(dpsPrisonerDetails)) {
        LOG.info("Prisoner ${dpsPrisonerDetails.prisonerId} already has the maximum number of VOs. Refund for visit ${visit.reference} will not be processed.")
        visitOrderHistoryService.logAllocationRefundedByVisitCancelled(dpsPrisonerDetails, visit.reference, visitOrderTypeUsed.name)
        return null
      }

      voUsedForVisit.apply {
        status = VisitOrderStatus.AVAILABLE
        visitReference = null
      }
    } else {
      // If none are found, find the negative VO used for the visit.
      val negativeVoUsedForVisit = dpsPrisonerDetails.negativeVisitOrders.firstOrNull { it.visitReference == visit.reference }

      when {
        // Aim to repay the original negative VO which was used by the visit.
        negativeVoUsedForVisit != null && negativeVoUsedForVisit.status == NegativeVisitOrderStatus.USED -> {
          LOG.info("Prisoner ${dpsPrisonerDetails.prisonerId} - refunding VO by removing negative VO")
          visitOrderTypeUsed = negativeVoUsedForVisit.type
          negativeVoUsedForVisit.apply {
            status = NegativeVisitOrderStatus.REPAID
            repaidDate = LocalDate.now()
            repaidReason = NegativeRepaymentReason.VISIT_ORDER_REFUND
          }
        }

        // If the original negative VO has already been repaid by something else (such as allocation),
        // find any negative USED VO, repay the first one.
        dpsPrisonerDetails.negativeVisitOrders.any { it.status == NegativeVisitOrderStatus.USED } -> {
          dpsPrisonerDetails.negativeVisitOrders.first { it.status == NegativeVisitOrderStatus.USED }.apply {
            visitOrderTypeUsed = type
            status = NegativeVisitOrderStatus.REPAID
            repaidDate = LocalDate.now()
            repaidReason = NegativeRepaymentReason.VISIT_ORDER_REFUND
          }
        }

        hasPrisonerReachedVoCap(dpsPrisonerDetails) -> {
          LOG.info("Prisoner ${dpsPrisonerDetails.prisonerId} already has the maximum number of VOs. Refund for visit ${visit.reference} will not be processed.")
          visitOrderHistoryService.logAllocationRefundedByVisitCancelled(dpsPrisonerDetails, visit.reference, visitOrderTypeUsed.name)
          return null
        }

        else -> {
          LOG.warn("No visit with reference ${visit.reference} associated with prisoner ${visit.prisonerId} found on visit allocation api. Creating VO.")
          dpsPrisonerDetails.visitOrders.add(visitOrdersUtil.createAvailableVisitOrder(dpsPrisonerDetails, VisitOrderType.VO))
        }
      }
    }

    visitOrderHistoryService.logAllocationRefundedByVisitCancelled(dpsPrisonerDetails, visit.reference, visitOrderTypeUsed.name)
    val changeLog = changeLogService.createLogAllocationRefundedByVisitCancelled(dpsPrisonerDetails, visit.reference)
    dpsPrisonerDetails.changeLogs.add(changeLog)

    telemetryClientService.trackEvent(
      TelemetryEventType.VO_REFUNDED_AFTER_VISIT_CANCELLATION,
      mapOf(
        "visitReference" to visit.reference,
        "prisonerId" to visit.prisonerId,
      ),
    )

    return changeLog.reference
  }

  private fun hasPrisonerReachedVoCap(dpsPrisonerDetails: PrisonerDetails): Boolean = dpsPrisonerDetails.visitOrders.count { it.type == VisitOrderType.VO && it.status in listOf(VisitOrderStatus.AVAILABLE, VisitOrderStatus.ACCUMULATED) } >= maxAccumulatedVisitOrders
}
