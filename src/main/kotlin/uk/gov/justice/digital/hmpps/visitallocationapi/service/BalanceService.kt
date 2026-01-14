package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceAdjustmentDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerDetailedBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VOBalancesUtil

@Service
class BalanceService(
  private val prisonerDetailsService: PrisonerDetailsService,
  private val prisonerBalanceAdjustmentService: PrisonerBalanceAdjustmentService,
  private val changeLogService: ChangeLogService,
  private val snsService: SnsService,
  private val voBalancesUtil: VOBalancesUtil,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  fun getPrisonerBalance(prisonerId: String): PrisonerBalanceDto? {
    LOG.info("Entered BalanceService - getPrisonerBalance for prisoner $prisonerId")

    // Try to fetch prisoner details, if not found, log and return null
    val prisonerDetails = prisonerDetailsService.getPrisonerDetails(prisonerId)
      ?: run {
        LOG.info("Prisoner $prisonerId not found in DB, returning null balance")
        return null
      }

    return voBalancesUtil.getPrisonerBalance(prisonerDetails)
  }

  @Transactional(readOnly = true)
  fun getPrisonerDetailedBalance(prisonerId: String): PrisonerDetailedBalanceDto? {
    LOG.info("Entered BalanceService - getPrisonerDetailedBalance for prisoner $prisonerId")

    // Try to fetch prisoner details, if not found, log and return null
    val prisonerDetails = prisonerDetailsService.getPrisonerDetails(prisonerId) ?: run {
      LOG.info("Prisoner $prisonerId not found in DB, returning null as detailed balance")
      return null
    }

    val detailedBalanceDto = voBalancesUtil.getPrisonersDetailedBalance(prisonerDetails)

    LOG.info("detailed VO and PVO balance for prisoner $prisonerId - $detailedBalanceDto")
    return detailedBalanceDto
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
  fun adjustPrisonerBalance(prisonerId: String, balanceAdjustmentDto: PrisonerBalanceAdjustmentDto): PrisonerBalanceDto? {
    LOG.info("Entered BalanceService - adjustPrisonerBalance for prisoner $prisonerId with adjustment details - $balanceAdjustmentDto")

    val changeLogReference = prisonerBalanceAdjustmentService.adjustPrisonerBalance(prisonerId, balanceAdjustmentDto)
    if (changeLogReference != null) {
      val changeLog = changeLogService.findChangeLogForPrisonerByReference(prisonerId, changeLogReference)
      if (changeLog != null) {
        snsService.sendPrisonAllocationAdjustmentCreatedEvent(changeLog)
      }
    }

    LOG.info("Adjusted prisoner balance for prisoner $prisonerId with adjustment details - $balanceAdjustmentDto")
    return getPrisonerBalance(prisonerId)
  }
}
