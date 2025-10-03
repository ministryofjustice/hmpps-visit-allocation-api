package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerDetailedBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus.USED
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus.ACCUMULATED
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus.AVAILABLE
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType.PVO
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType.VO
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails

@Service
class BalanceService(private val prisonerDetailsService: PrisonerDetailsService) {
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

    return prisonerDetails.getBalance()
  }

  @Transactional(readOnly = true)
  fun getPrisonerDetailedBalance(prisonerId: String): PrisonerDetailedBalanceDto? {
    LOG.info("Entered BalanceService - getPrisonerDetailedBalance for prisoner $prisonerId")

    // Try to fetch prisoner details, if not found, log and return null
    val prisonerDetails = prisonerDetailsService.getPrisonerDetails(prisonerId) ?: run {
      LOG.info("Prisoner $prisonerId not found in DB, returning null as detailed balance")
      return null
    }

    val detailedBalanceDto = PrisonerDetailedBalanceDto(
      prisonerId = prisonerDetails.prisonerId,
      availableVos = getAvailableVOBalance(prisonerDetails),
      accumulatedVos = getAccumulatedVOBalance(prisonerDetails),
      negativeVos = getNegativeVOBalance(prisonerDetails),
      availablePvos = getAvailablePVOBalance(prisonerDetails),
      negativePvos = getNegativePVOBalance(prisonerDetails),
      lastVoAllocatedDate = prisonerDetails.lastVoAllocatedDate,
      lastPvoAllocatedDate = prisonerDetails.lastPvoAllocatedDate,
    )

    LOG.info("detailed VO and PVO balance for prisoner $prisonerId - $detailedBalanceDto")
    return detailedBalanceDto
  }

  private fun getAvailableVOBalance(prisonerDetails: PrisonerDetails) = prisonerDetails.visitOrders.count { it.type == VO && it.status == AVAILABLE }

  private fun getAccumulatedVOBalance(prisonerDetails: PrisonerDetails) = prisonerDetails.visitOrders.count { it.type == VO && it.status == ACCUMULATED }

  private fun getNegativeVOBalance(prisonerDetails: PrisonerDetails) = prisonerDetails.negativeVisitOrders.count { it.type == VO && it.status == USED }

  private fun getAvailablePVOBalance(prisonerDetails: PrisonerDetails) = prisonerDetails.visitOrders.count { it.type == PVO && it.status == AVAILABLE }

  private fun getNegativePVOBalance(prisonerDetails: PrisonerDetails) = prisonerDetails.negativeVisitOrders.count { it.type == PVO && it.status == USED }
}
