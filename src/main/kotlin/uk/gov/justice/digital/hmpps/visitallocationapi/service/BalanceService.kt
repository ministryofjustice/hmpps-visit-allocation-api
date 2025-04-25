package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
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
    val prisonerDetails = prisonerDetailsService.getPrisoner(prisonerId)
      ?: run {
        LOG.info("Prisoner $prisonerId not found in DB, returning null balance")
        return null
      }

    return PrisonerBalanceDto(
      prisonerId = prisonerId,
      voBalance = getVoBalance(prisonerDetails),
      pvoBalance = getPvoBalance(prisonerDetails),
    )
  }

  private fun getVoBalance(prisonerDetails: PrisonerDetails): Int = prisonerDetails.visitOrders.count { it.type == VisitOrderType.VO && it.status in listOf(VisitOrderStatus.AVAILABLE, VisitOrderStatus.ACCUMULATED) }
    .minus(prisonerDetails.negativeVisitOrders.count { it.type == VisitOrderType.VO && it.status == NegativeVisitOrderStatus.USED })

  private fun getPvoBalance(prisonerDetails: PrisonerDetails): Int = prisonerDetails.visitOrders.count { it.type == VisitOrderType.PVO && it.status in listOf(VisitOrderStatus.AVAILABLE) }
    .minus(prisonerDetails.negativeVisitOrders.count { it.type == VisitOrderType.PVO && it.status == NegativeVisitOrderStatus.USED })
}
