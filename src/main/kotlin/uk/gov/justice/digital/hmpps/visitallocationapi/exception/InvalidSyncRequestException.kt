package uk.gov.justice.digital.hmpps.visitallocationapi.exception

import jakarta.validation.ValidationException
import java.util.function.Supplier

class InvalidSyncRequestException(message: String? = null, cause: Throwable? = null) :
  ValidationException(message, cause),
  Supplier<InvalidSyncRequestException> {
  override fun get(): InvalidSyncRequestException = InvalidSyncRequestException(message, cause)
}
