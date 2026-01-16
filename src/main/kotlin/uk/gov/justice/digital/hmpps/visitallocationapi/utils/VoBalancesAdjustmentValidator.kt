package uk.gov.justice.digital.hmpps.visitallocationapi.utils

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceAdjustmentDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerDetailedBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.PrisonerBalanceAdjustmentValidationErrorCodes
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.VoBalanceAdjustmentException
import kotlin.math.absoluteValue

@Component
class VoBalancesAdjustmentValidator(
  @param:Value("\${max.visit-orders:26}") val maxVisitOrders: Int,
) {
  fun validate(prisonerDetailedBalance: PrisonerDetailedBalanceDto, balanceAdjustmentDto: PrisonerBalanceAdjustmentDto) {
    val validationErrors = mutableListOf<PrisonerBalanceAdjustmentValidationErrorCodes>()

    if (isVoOrPvoBalanceNotUpdated(balanceAdjustmentDto)) {
      validationErrors.add(PrisonerBalanceAdjustmentValidationErrorCodes.VO_OR_PVO_NOT_SUPPLIED)
    } else {
      if (isVOCountAfterAdjustmentAboveMaxLevel(prisonerDetailedBalance.voBalance, balanceAdjustmentDto.voAmount)) {
        validationErrors.add(PrisonerBalanceAdjustmentValidationErrorCodes.VO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX)
      } else if (isVOCountAfterAdjustmentBelowMinLevel(prisonerDetailedBalance.voBalance, balanceAdjustmentDto.voAmount)) {
        validationErrors.add(PrisonerBalanceAdjustmentValidationErrorCodes.VO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO)
      }

      if (isVOCountAfterAdjustmentAboveMaxLevel(prisonerDetailedBalance.pvoBalance, balanceAdjustmentDto.pvoAmount)) {
        validationErrors.add(PrisonerBalanceAdjustmentValidationErrorCodes.PVO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX)
      } else if (isVOCountAfterAdjustmentBelowMinLevel(prisonerDetailedBalance.pvoBalance, balanceAdjustmentDto.pvoAmount)) {
        validationErrors.add(PrisonerBalanceAdjustmentValidationErrorCodes.PVO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO)
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
