package uk.gov.justice.digital.hmpps.visitallocationapi.utils

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceAdjustmentDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerDetailedBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.VoBalanceAdjustmentException
import kotlin.math.absoluteValue

@Component
class VoBalancesAdjustmentValidator(
  @param:Value("\${max.visit-orders:26}") val maxVisitOrders: Int,
) {
/*
TODO - should we just set it to 26 instead of above?
  companion object {
    private const val MAX_VISIT_ORDERS = 26
  }
*/
  fun validate(prisonerDetailedBalance: PrisonerDetailedBalanceDto, balanceAdjustmentDto: PrisonerBalanceAdjustmentDto) {
    val validationErrors = mutableListOf<String>()

    if (isVoOrPvoBalanceNotUpdated(balanceAdjustmentDto)) {
      validationErrors.add("Either voAmount or pvoAmount must be provided")
    } else {
      if (isVOCountAfterAdjustmentAboveMaxLevel(prisonerDetailedBalance.voBalance, balanceAdjustmentDto.voAmount)) {
        validationErrors.add("VO count after adjustment will take it past max allowed")
      } else if (isVOCountAfterAdjustmentBelowMinLevel(prisonerDetailedBalance.voBalance, balanceAdjustmentDto.voAmount)) {
        validationErrors.add("VO count after adjustment will take it below zero")
      }

      if (isVOCountAfterAdjustmentAboveMaxLevel(prisonerDetailedBalance.pvoBalance, balanceAdjustmentDto.pvoAmount)) {
        validationErrors.add("PVO count after adjustment will take it past max allowed")
      } else if (isVOCountAfterAdjustmentBelowMinLevel(prisonerDetailedBalance.pvoBalance, balanceAdjustmentDto.pvoAmount)) {
        validationErrors.add("PVO count after adjustment will take it below zero")
      }
    }

    if (validationErrors.isNotEmpty()) {
      throw VoBalanceAdjustmentException(validationErrors.toTypedArray())
    }
  }

  private fun isVoOrPvoBalanceNotUpdated(balanceAdjustmentDto: PrisonerBalanceAdjustmentDto): Boolean {
    val voAdjustmentAmount = balanceAdjustmentDto.voAmount ?: 0
    val pvoAdjustmentAmount = balanceAdjustmentDto.pvoAmount ?: 0
    return (voAdjustmentAmount == 0 && pvoAdjustmentAmount == 0)
  }

  private fun isVOCountAfterAdjustmentAboveMaxLevel(existingBalance: Int, adjustmentAmount: Int?): Boolean {
    val voAdjustmentAmount = adjustmentAmount ?: 0
    return if (voAdjustmentAmount > 0) {
      ((existingBalance + voAdjustmentAmount) > maxVisitOrders)
    } else {
      false
    }
  }

  private fun isVOCountAfterAdjustmentBelowMinLevel(existingBalance: Int, adjustmentAmount: Int?): Boolean {
    val voAdjustmentAmount = adjustmentAmount ?: 0
    return if (voAdjustmentAmount < 0) {
      ((existingBalance - voAdjustmentAmount.absoluteValue) < 0)
    } else {
      false
    }
  }
}
