package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
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
    val positiveVoBalance = positiveBalance.firstOrNull { it.type == VisitOrderType.VO }?.balance ?: 0
    val positivePvoBalance = positiveBalance.firstOrNull { it.type == VisitOrderType.PVO }?.balance ?: 0

    val negativeBalance = negativeVisitOrderRepository.getPrisonerNegativeBalance(prisonerId)
    val negativeVoBalance = negativeBalance.firstOrNull { it.type == NegativeVisitOrderType.NEGATIVE_VO }?.balance ?: 0
    val negativePvoBalance = negativeBalance.firstOrNull { it.type == NegativeVisitOrderType.NEGATIVE_PVO }?.balance ?: 0

    return PrisonerBalanceDto(
      prisonerId = prisonerId,
      voBalance = positiveVoBalance - negativeVoBalance,
      pvoBalance = positivePvoBalance - negativePvoBalance,
    )
  }
}
