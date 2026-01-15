package uk.gov.justice.digital.hmpps.visitallocationapi.config

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.PrisonerBalanceAdjustmentValidationErrorCodes
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.InvalidSyncRequestException
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.PublishEventException
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.VoBalanceAdjustmentException

@RestControllerAdvice
class VisitAllocationApiExceptionHandler(private val telemetryClient: TelemetryClient) {
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: ValidationException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(BAD_REQUEST)
    .body(
      ErrorResponse(
        status = BAD_REQUEST,
        userMessage = "Validation failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("Validation exception: {}", e.message) }

  @ExceptionHandler(InvalidSyncRequestException::class)
  fun handleInvalidSyncRequestException(e: InvalidSyncRequestException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(BAD_REQUEST)
    .body(
      ErrorResponse(
        status = BAD_REQUEST,
        userMessage = "${e.message}",
        developerMessage = e.message,
      ),
    )

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleMethodArgumentException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(BAD_REQUEST)
    .body(
      ErrorResponse(
        status = BAD_REQUEST,
        userMessage = if (e.errorCount > 1) "Invalid Arguments" else "Invalid Argument",
        developerMessage = e.message,
      ),
    ).also { log.info("Method Argument Validation exception: {}", e.message) }

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleNoResourceFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(NOT_FOUND)
    .body(
      ErrorResponse(
        status = NOT_FOUND,
        userMessage = "No resource found failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("No resource found exception: {}", e.message) }

  @ExceptionHandler(NotFoundException::class)
  fun handleNotFoundException(e: NotFoundException): ResponseEntity<ErrorResponse?>? {
    log.error("Not Found exception caught: {}", e.message)
    return ResponseEntity
      .status(NOT_FOUND)
      .body(
        ErrorResponse(
          status = NOT_FOUND,
          userMessage = "not found: ${e.cause?.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(FORBIDDEN)
    .body(
      ErrorResponse(
        status = FORBIDDEN,
        userMessage = "Forbidden: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.debug("Forbidden (403) returned: {}", e.message) }

  @ExceptionHandler(Exception::class)
  fun handleException(e: Exception): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(INTERNAL_SERVER_ERROR)
    .body(
      ErrorResponse(
        status = INTERNAL_SERVER_ERROR,
        userMessage = "Unexpected error: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.error("Unexpected exception", e) }

  @ExceptionHandler(PublishEventException::class)
  fun handlePublishEventException(e: PublishEventException): ResponseEntity<ErrorResponse?>? {
    log.error("Publish event exception caught: {}", e.message)
    val error = ErrorResponse(
      status = INTERNAL_SERVER_ERROR,
      userMessage = "Failed to publish event: ${e.cause?.message}",
      developerMessage = e.message,
    )
    telemetryClient.trackEvent(
      "allocation-api-publish-event-error",
      mapOf(
        "status" to error.status.toString(),
        "message" to (error.developerMessage?.take(256) ?: ""),
        "cause" to (error.userMessage?.take(256) ?: ""),
      ),
      null,
    )

    return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(error)
  }

  @ExceptionHandler(VoBalanceAdjustmentException::class)
  fun handleVoBalanceAdjustmentException(e: VoBalanceAdjustmentException): ResponseEntity<ManualBalanceAdjustmentValidationErrorResponse?>? {
    log.error("Validation exception", e)
    return ResponseEntity
      .status(HttpStatus.UNPROCESSABLE_ENTITY)
      .body(
        ManualBalanceAdjustmentValidationErrorResponse(
          validationErrorCodes = e.errorCodes.toSet(),
          userMessage = "Validation for balance adjustment failed",
          developerMessage = "Validation for balance adjustment failed: ${e.errorCodes.joinToString { it.description }}",
        ),
      )
  }
}

open class ErrorResponse(
  val status: Int,
  val errorCode: Int? = null,
  open val userMessage: String? = null,
  open val developerMessage: String? = null,
  val moreInfo: String? = null,
) {
  constructor(
    status: HttpStatus,
    errorCode: Int? = null,
    userMessage: String? = null,
    developerMessage: String? = null,
    moreInfo: String? = null,
  ) :
    this(status.value(), errorCode, userMessage, developerMessage, moreInfo)
}

data class ManualBalanceAdjustmentValidationErrorResponse(
  val validationErrorCodes: Set<PrisonerBalanceAdjustmentValidationErrorCodes>,
  override val userMessage: String? = null,
  override val developerMessage: String? = null,
) : ErrorResponse(status = HttpStatus.UNPROCESSABLE_ENTITY, userMessage = userMessage, developerMessage = developerMessage)
