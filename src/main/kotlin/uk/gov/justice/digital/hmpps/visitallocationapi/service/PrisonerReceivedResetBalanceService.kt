package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeRepaymentReason
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import java.time.LocalDate
import java.util.UUID

@Transactional
@Service
class PrisonerReceivedResetBalanceService(
  private val prisonerDetailsService: PrisonerDetailsService,
  private val changeLogService: ChangeLogService,
  private val telemetryClientService: TelemetryClientService,
  private val visitOrderHistoryService: VisitOrderHistoryService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processPrisonerReceivedResetBalance(prisonerId: String, reason: PrisonerReceivedReasonType): UUID {
    val dpsPrisonerDetails = prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)
      ?: prisonerDetailsService.createPrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)

    dpsPrisonerDetails.visitOrders
      .filter { it.status in listOf(VisitOrderStatus.AVAILABLE, VisitOrderStatus.ACCUMULATED) }
      .forEach {
        it.status = VisitOrderStatus.EXPIRED
        it.expiryDate = LocalDate.now()
      }

    dpsPrisonerDetails.negativeVisitOrders
      .filter { it.status == NegativeVisitOrderStatus.USED }
      .forEach {
        it.status = NegativeVisitOrderStatus.REPAID
        it.repaidDate = LocalDate.now()
        it.repaidReason = NegativeRepaymentReason.PRISONER_RECEIVED_RESET
      }

    visitOrderHistoryService.logPrisonerBalanceReset(dpsPrisonerDetails, reason)
    val changeLog = changeLogService.createLogPrisonerBalanceReset(dpsPrisonerDetails, reason)
    dpsPrisonerDetails.changeLogs.add(changeLog)

    telemetryClientService.trackEvent(
      TelemetryEventType.VO_PRISONER_BALANCE_RESET,
      mapOf(
        "prisonerId" to prisonerId,
        "reason" to reason.name,
      ),
    )

    return changeLog.reference
  }
}
