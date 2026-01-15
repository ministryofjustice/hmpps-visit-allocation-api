package uk.gov.justice.digital.hmpps.visitallocationapi.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceAdjustmentDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerDetailedBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.AdjustmentReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.PrisonerBalanceAdjustmentValidationErrorCodes.PVO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.PrisonerBalanceAdjustmentValidationErrorCodes.PVO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.PrisonerBalanceAdjustmentValidationErrorCodes.VO_OR_PVO_NOT_SUPPLIED
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.PrisonerBalanceAdjustmentValidationErrorCodes.VO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.PrisonerBalanceAdjustmentValidationErrorCodes.VO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.VoBalanceAdjustmentException
import java.time.LocalDate

class VoBalancesAdjustmentValidatorTest {
  private val voBalancesAdjustmentValidator = VoBalancesAdjustmentValidator(26)

  @Test
  fun `when VO adjustment and PVO both are null an exception is thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 0, pvoBalance = 0)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = null,
      pvoAmount = null,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(1)
    assertThat(exception.errorCodes[0]).isEqualTo(VO_OR_PVO_NOT_SUPPLIED)
  }

  @Test
  fun `when VO adjustment and PVO both are zero an exception is thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 0, pvoBalance = 0)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = 0,
      pvoAmount = 0,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(1)
    assertThat(exception.errorCodes[0]).isEqualTo(VO_OR_PVO_NOT_SUPPLIED)
  }

  @Test
  fun `when VO adjustment takes VO limit above max value an exception is thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 10, pvoBalance = 4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = 17,
      pvoAmount = 0,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(1)
    assertThat(exception.errorCodes[0]).isEqualTo(VO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX)
  }

  @Test
  fun `when VO adjustment does not take VO limit above max value an exception is not thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 22, pvoBalance = 4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = 4,
      pvoAmount = 0,
    )

    assertDoesNotThrow {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
  }

  @Test
  fun `when VO balance is already below 0 and Vos are reduced an exception is thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = -1, pvoBalance = 4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = -2,
      pvoAmount = 0,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(1)
    assertThat(exception.errorCodes[0]).isEqualTo(VO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO)
  }

  @Test
  fun `when PVO balance is already below 0 and Pvos are reduced an exception is thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 11, pvoBalance = -4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = -2,
      pvoAmount = -2,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(1)
    assertThat(exception.errorCodes[0]).isEqualTo(PVO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO)
  }

  @Test
  fun `when VO balance is already below 0 and adding VOs still keeps VO count below 0 an exception is not thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = -14, pvoBalance = 4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = 4,
      pvoAmount = 0,
    )

    assertDoesNotThrow {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
  }

  @Test
  fun `when PVO balance is already below 0 and adding PVOs still keeps PVO count below 0 an exception is not thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = -14, pvoBalance = -4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = 4,
      pvoAmount = 2,
    )

    assertDoesNotThrow {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
  }

  @Test
  fun `when VO adjustment takes VO limit below zero an exception is thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 10, pvoBalance = 4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = -11,
      pvoAmount = 0,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(1)
    assertThat(exception.errorCodes[0]).isEqualTo(VO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO)
  }

  @Test
  fun `when VO adjustment does not take VO limit below zero an exception is not thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 10, pvoBalance = 4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = -10,
      pvoAmount = 0,
    )

    assertDoesNotThrow {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
  }

  @Test
  fun `when PVO adjustment takes PVO limit above max value an exception is thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 10, pvoBalance = 22)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = 4,
      pvoAmount = 5,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(1)
    assertThat(exception.errorCodes[0]).isEqualTo(PVO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX)
  }

  @Test
  fun `when PVO adjustment does not take PVO limit above max value an exception is not thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 10, pvoBalance = 22)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = 4,
      pvoAmount = 4,
    )

    assertDoesNotThrow {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
  }

  @Test
  fun `when PVO adjustment takes PVO limit below zero an exception is thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 22, pvoBalance = 4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = -3,
      pvoAmount = -5,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(1)
    assertThat(exception.errorCodes[0]).isEqualTo(PVO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO)
  }

  @Test
  fun `when PVO adjustment does not take PVO limit below zero an exception is not thrown`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 10, pvoBalance = 4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = -10,
      pvoAmount = 0,
    )

    assertDoesNotThrow {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
  }

  @Test
  fun `when VO and PVO adjustment takes VO and PVO limit above max value an exception is thrown with multiple errorCodes`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 23, pvoBalance = 22)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = 4,
      pvoAmount = 5,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(2)
    assertThat(exception.errorCodes[0]).isEqualTo(VO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX)
    assertThat(exception.errorCodes[1]).isEqualTo(PVO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX)
  }

  @Test
  fun `when VO and PVO adjustment takes VO and PVO limit below min value an exception is thrown with multiple errorCodes`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 2, pvoBalance = 4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = -3,
      pvoAmount = -5,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(2)
    assertThat(exception.errorCodes[0]).isEqualTo(VO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO)
    assertThat(exception.errorCodes[1]).isEqualTo(PVO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO)
  }

  @Test
  fun `when VO and PVO adjustment takes VO above max and PVO limit below min value an exception is thrown with multiple errorCodes`() {
    // Given
    val prisonerDetailedBalance = createPrisonerDetailedBalance(voBalance = 24, pvoBalance = 4)

    val balanceAdjustmentDto = createPrisonerBalanceAdjustmentDto(
      voAmount = 3,
      pvoAmount = -5,
    )

    val exception = assertThrows<VoBalanceAdjustmentException> {
      voBalancesAdjustmentValidator.validate(prisonerDetailedBalance, balanceAdjustmentDto)
    }
    assertThat(exception.errorCodes.size).isEqualTo(2)
    assertThat(exception.errorCodes[0]).isEqualTo(VO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX)
    assertThat(exception.errorCodes[1]).isEqualTo(PVO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO)
  }

  private fun createPrisonerDetailedBalance(
    voBalance: Int,
    pvoBalance: Int,
  ) = PrisonerDetailedBalanceDto(
    prisonerId = "A1234AA",
    voBalance = voBalance,
    availableVos = voBalance - 2 + 3,
    accumulatedVos = 2,
    negativeVos = -3,
    pvoBalance = pvoBalance,
    availablePvos = pvoBalance + 3,
    negativePvos = 3,
    lastVoAllocatedDate = LocalDate.now(),
    nextVoAllocationDate = LocalDate.now(),
    lastPvoAllocatedDate = null,
    nextPvoAllocationDate = null,
  )

  private fun createPrisonerBalanceAdjustmentDto(
    voAmount: Int?,
    pvoAmount: Int?,
  ) = PrisonerBalanceAdjustmentDto(
    voAmount = voAmount,
    pvoAmount = pvoAmount,
    adjustmentReasonType = AdjustmentReasonType.GOVERNOR_ADJUSTMENT,
    adjustmentReasonText = "test",
    userName = "DS",
  )
}
