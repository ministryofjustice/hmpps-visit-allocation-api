package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.projections.PrisonerBalance
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository

@Service
class BalanceService(
  private val visitOrderRepository: VisitOrderRepository,
  private val negativeVisitOrderRepository: NegativeVisitOrderRepository,
  private val prisonerDetailsService: PrisonerDetailsService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  fun getPrisonerBalance(prisonerId: String): PrisonerBalanceDto? {
    LOG.info("Entered BalanceService - getPrisonerBalance for prisoner $prisonerId")

    // If prisoner doesn't exist in DB, return null. Balance endpoint uses null to throw a NOT_FOUND exception.
    if (prisonerDetailsService.getPrisoner(prisonerId) == null) {
      LOG.info("Prisoner $prisonerId not found in DB, returning null balance")
      return null
    }

    val positiveBalance = visitOrderRepository.getPrisonerPositiveBalance(prisonerId)
    val positiveVoBalance = getBalanceForVoType(positiveBalance, VisitOrderType.VO)
    val positivePvoBalance = getBalanceForVoType(positiveBalance, VisitOrderType.PVO)

    val negativeBalance = negativeVisitOrderRepository.getPrisonerNegativeBalance(prisonerId)
    val negativeVoBalance = getBalanceForVoType(negativeBalance, VisitOrderType.VO)
    val negativePvoBalance = getBalanceForVoType(negativeBalance, VisitOrderType.PVO)

    return PrisonerBalanceDto(
      prisonerId = prisonerId,
      voBalance = (positiveVoBalance - negativeVoBalance),
      pvoBalance = (positivePvoBalance - negativePvoBalance),
    )
  }

  private fun getBalanceForVoType(prisonerBalance: List<PrisonerBalance>, type: VisitOrderType): Int = prisonerBalance.firstOrNull { it.type == type }?.balance ?: 0
}
