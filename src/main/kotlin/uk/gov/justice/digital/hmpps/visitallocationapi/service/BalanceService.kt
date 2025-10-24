package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerDetailedBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VOBalancesUtil

@Service
class BalanceService(
  private val prisonerDetailsService: PrisonerDetailsService,
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
}
