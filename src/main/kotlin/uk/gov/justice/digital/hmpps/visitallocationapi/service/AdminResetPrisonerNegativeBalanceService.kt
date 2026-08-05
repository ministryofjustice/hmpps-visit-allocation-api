package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeRepaymentReason
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import java.time.LocalDate
import java.util.UUID

@Service
class AdminResetPrisonerNegativeBalanceService(
  private val prisonerDetailsService: PrisonerDetailsService,
  private val changeLogService: ChangeLogService,
  private val telemetryClientService: TelemetryClientService,
  private val visitOrderHistoryService: VisitOrderHistoryService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun processAdminResetPrisonerNegativeBalance(prisonerId: String): UUID? {
    val details = prisonerDetailsService.getPrisonerDetails(prisonerId)
    if (details == null) {
      LOG.info("Prisoner $prisonerId not found in DPS DB, skipping admin reset negative balance")
      return null
    }

    val used = details.negativeVisitOrders.filter { it.status == NegativeVisitOrderStatus.USED }
    if (used.isEmpty()) {
      LOG.info("Prisoner $prisonerId has no negative VOs, skipping admin reset negative balance")
      return null
    }

    val amountToRepay = used.count()
    used.forEach {
      it.status = NegativeVisitOrderStatus.REPAID
      it.repaidDate = LocalDate.now()
      it.repaidReason = NegativeRepaymentReason.ADMIN_RESET
    }

    visitOrderHistoryService.logPrisonerNegativeBalanceAdminReset(details)
    val changeLog = changeLogService.createLogPrisonerNegativeBalanceAdminReset(details)
    details.changeLogs.add(changeLog)

    telemetryClientService.trackEvent(
      TelemetryEventType.VO_PRISONER_NEGATIVE_BALANCE_ADMIN_RESET,
      mapOf(
        "prisonerId" to prisonerId,
      ),
    )

    LOG.info("Admin reset negative balance by repaying $amountToRepay for prisoner $prisonerId")
    return changeLog.reference
  }
}
