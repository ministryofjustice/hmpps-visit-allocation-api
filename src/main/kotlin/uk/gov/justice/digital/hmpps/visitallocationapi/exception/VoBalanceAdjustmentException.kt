package uk.gov.justice.digital.hmpps.visitallocationapi.exception

import jakarta.validation.ValidationException
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.PrisonerBalanceAdjustmentValidationErrorCodes
import java.util.function.Supplier

class VoBalanceAdjustmentException(val errorCodes: Array<PrisonerBalanceAdjustmentValidationErrorCodes>) :
  ValidationException("Failed to validate balance adjustment"),
  Supplier<VoBalanceAdjustmentException> {
  override fun get(): VoBalanceAdjustmentException = VoBalanceAdjustmentException(errorCodes)

  constructor(errorCode: PrisonerBalanceAdjustmentValidationErrorCodes) : this(arrayOf(errorCode))
}
