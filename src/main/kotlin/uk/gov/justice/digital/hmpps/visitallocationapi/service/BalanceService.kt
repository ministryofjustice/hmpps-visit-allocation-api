package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto

@Service
class BalanceService(private val prisonerDetailsService: PrisonerDetailsService) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
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
}
