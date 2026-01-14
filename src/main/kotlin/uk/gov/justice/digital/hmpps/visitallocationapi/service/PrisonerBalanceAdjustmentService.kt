package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceAdjustmentDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerDetailedBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ProcessPrisonerService.Companion.LOG
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VOBalancesUtil
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VisitOrdersUtil
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VoBalancesAdjustmentValidator
import java.util.UUID
import kotlin.math.absoluteValue

@Service
class PrisonerBalanceAdjustmentService(
  private val prisonerDetailsService: PrisonerDetailsService,
  private val visitOrderHistoryService: VisitOrderHistoryService,
  private val changeLogService: ChangeLogService,
  private val telemetryClientService: TelemetryClientService,
  private val voBalancesUtil: VOBalancesUtil,
  private val visitOrdersUtil: VisitOrdersUtil,
  private val voBalancesAdjustmentValidator: VoBalancesAdjustmentValidator,
) {
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun adjustPrisonerBalance(prisonerId: String, balanceAdjustmentDto: PrisonerBalanceAdjustmentDto): UUID? {
    LOG.debug("Adjusting balance for prisoner {} with adjustment details of {}", prisonerId, balanceAdjustmentDto)
    val dpsPrisoner = prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId) ?: throw NotFoundException("Prisoner $prisonerId not found")
    val detailedBalanceBefore = voBalancesUtil.getPrisonersDetailedBalance(dpsPrisoner)
    voBalancesAdjustmentValidator.validate(detailedBalanceBefore, balanceAdjustmentDto)
    adjustVOs(dpsPrisoner, detailedBalanceBefore, balanceAdjustmentDto.voAmount ?: 0, VisitOrderType.VO)
    adjustVOs(dpsPrisoner, detailedBalanceBefore, balanceAdjustmentDto.pvoAmount ?: 0, VisitOrderType.PVO)

    visitOrderHistoryService.logPrisonerManualBalanceAdjustment(dpsPrisoner, userName = balanceAdjustmentDto.userName, comment = balanceAdjustmentDto.adjustmentReasonText, adjustmentReasonType = balanceAdjustmentDto.adjustmentReasonType)
    val changeLog = createChangeLog(dpsPrisoner, balanceAdjustmentDto)
    dpsPrisoner.changeLogs.add(changeLog)

    sendTelemetryEvent(dpsPrisoner, balanceAdjustmentDto)
    val detailedBalanceAfter = voBalancesUtil.getPrisonersDetailedBalance(dpsPrisoner)
    LOG.info("Finished adjusting balance for prisoner {} with adjustment details of {}, balance before {}, balance after {}", prisonerId, balanceAdjustmentDto, detailedBalanceBefore, detailedBalanceAfter)
    return changeLog.reference
  }

  private fun adjustVOs(dpsPrisoner: PrisonerDetails, detailedBalance: PrisonerDetailedBalanceDto, adjustmentAmount: Int, visitOrderType: VisitOrderType) {
    when {
      (adjustmentAmount > 0) -> addPrisonerVos(dpsPrisoner, adjustmentAmount, visitOrderType)
      (adjustmentAmount < 0) -> markPrisonerVosAsUsed(dpsPrisoner, detailedBalance, adjustmentAmount.absoluteValue, visitOrderType)
      else -> BalanceService.Companion.LOG.trace("No adjustment to {}s for prisoner {} as adjustment amount is 0", visitOrderType.name, dpsPrisoner.prisonerId)
    }
  }

  private fun addPrisonerVos(dpsPrisoner: PrisonerDetails, adjustmentAmount: Int, visitOrderType: VisitOrderType) {
    dpsPrisoner.visitOrders.addAll(visitOrdersUtil.generateVos(dpsPrisoner, adjustmentAmount, visitOrderType))
  }

  private fun markPrisonerVosAsUsed(dpsPrisoner: PrisonerDetails, detailedBalanceDto: PrisonerDetailedBalanceDto, adjustmentAmount: Int, visitOrderType: VisitOrderType) {
    BalanceService.Companion.LOG.trace("Marking {} {}s as used for prisoner {}", adjustmentAmount, visitOrderType, dpsPrisoner.prisonerId)
    val accumulatedVosToMark = if (visitOrderType == VisitOrderType.VO) {
      detailedBalanceDto.accumulatedVos.coerceAtMost(adjustmentAmount)
    } else {
      0
    }

    val availableVosToMark = if (visitOrderType == VisitOrderType.VO) {
      detailedBalanceDto.availableVos.coerceAtMost(adjustmentAmount - accumulatedVosToMark)
    } else {
      detailedBalanceDto.availablePvos.coerceAtMost(adjustmentAmount)
    }

    markVosAsUsed(dpsPrisoner, accumulatedVosToMark, visitOrderType, VisitOrderStatus.ACCUMULATED)
    markVosAsUsed(dpsPrisoner, availableVosToMark, visitOrderType, VisitOrderStatus.AVAILABLE)
  }

  private fun markVosAsUsed(dpsPrisoner: PrisonerDetails, total: Int, visitOrderType: VisitOrderType, visitOrderStatus: VisitOrderStatus) {
    if (total > 0) {
      dpsPrisoner.visitOrders.sortedByDescending { it.createdTimestamp }
        .filter { it.type == visitOrderType && it.status == visitOrderStatus }
        .take(total).forEach { vo ->
          vo.status = VisitOrderStatus.USED
        }
    }
  }

  private fun createChangeLog(dpsPrisoner: PrisonerDetails, balanceAdjustmentDto: PrisonerBalanceAdjustmentDto): ChangeLog = changeLogService.createLogPrisonerBalanceAdjusted(dpsPrisoner, userId = balanceAdjustmentDto.userName)

  private fun sendTelemetryEvent(dpsPrisoner: PrisonerDetails, balanceAdjustmentDto: PrisonerBalanceAdjustmentDto) {
    BalanceService.Companion.LOG.info("Sending telemetry event for manual balance adjustment for prisoner ${dpsPrisoner.prisonerId}")
    val telemetryClientProperties = mapOf(
      "prisonerId" to dpsPrisoner.prisonerId,
      "voAdjusted" to (balanceAdjustmentDto.voAmount ?: 0).toString(),
      "pvoAdjusted" to (balanceAdjustmentDto.pvoAmount ?: 0).toString(),
      "adjustmentReasonType" to balanceAdjustmentDto.adjustmentReasonType.name,
      "userName" to balanceAdjustmentDto.userName,
    )

    telemetryClientService.trackEvent(
      TelemetryEventType.MANUAL_PRISONER_BALANCE_ADJUSTMENT,
      telemetryClientProperties,
    )
  }
}
