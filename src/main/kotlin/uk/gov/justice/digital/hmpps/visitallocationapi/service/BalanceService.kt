package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.projections.NegativePrisonerBalance
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.projections.PositivePrisonerBalance
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository

@Service
class BalanceService(
  private val visitOrderRepository: VisitOrderRepository,
  private val negativeVisitOrderRepository: NegativeVisitOrderRepository,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  fun getPrisonerBalance(prisonerId: String): PrisonerBalanceDto {
    LOG.info("Entered BalanceService - getPrisonerBalance for prisoner $prisonerId")

    val positiveBalance = visitOrderRepository.getPrisonerPositiveBalance(prisonerId)
    val positiveVoBalance = getBalanceForVoType(positiveBalance, VisitOrderType.VO)
    val positivePvoBalance = getBalanceForVoType(positiveBalance, VisitOrderType.PVO)

    val negativeBalance = negativeVisitOrderRepository.getPrisonerNegativeBalance(prisonerId)
    val negativeVoBalance = getBalanceForNegativeVoType(negativeBalance, NegativeVisitOrderType.NEGATIVE_VO)
    val negativePvoBalance = getBalanceForNegativeVoType(negativeBalance, NegativeVisitOrderType.NEGATIVE_PVO)

    return PrisonerBalanceDto(
      prisonerId = prisonerId,
      voBalance = positiveVoBalance - negativeVoBalance,
      pvoBalance = positivePvoBalance - negativePvoBalance,
    )
  }

  private fun getBalanceForVoType(positiveBalance: List<PositivePrisonerBalance>, type: VisitOrderType): Int = positiveBalance.firstOrNull { it.type == type }?.balance ?: 0

  private fun getBalanceForNegativeVoType(negativeBalance: List<NegativePrisonerBalance>, type: NegativeVisitOrderType): Int = negativeBalance.firstOrNull { it.type == type }?.balance ?: 0
}
