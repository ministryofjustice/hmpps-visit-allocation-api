package uk.gov.justice.digital.hmpps.visitallocationapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__NOMIS_API
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceAdjustmentDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerDetailedBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.visitallocationapi.service.BalanceService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

const val PRISONER_ID_PATH = "/visits/allocation/prisoner/{prisonerId}"
const val VO_BALANCE = "$PRISONER_ID_PATH/balance"
const val VO_BALANCE_DETAILED = "$VO_BALANCE/detailed"

@RestController
class BalanceController(val balanceService: BalanceService) {
  @PreAuthorize("hasAnyRole('$ROLE_VISIT_ALLOCATION_API__NOMIS_API', '$ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API')")
  @GetMapping(VO_BALANCE)
  @Operation(
    summary = "Endpoint to get a prisoners current balance.",
    description = "Takes a prisoner id and return their current visit order balance.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner balance returned.",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get prisoner balance.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Prisoner balance not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonerBalance(
    @Schema(description = "prisonerId", example = "AA123456", required = true)
    @PathVariable
    prisonerId: String,
  ): PrisonerBalanceDto = balanceService.getPrisonerBalance(prisonerId) ?: throw NotFoundException("Prisoner $prisonerId not found")

  @PreAuthorize("hasRole('$ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API')")
  @GetMapping(VO_BALANCE_DETAILED)
  @Operation(
    summary = "Endpoint to get a prisoners current balance, detailed version.",
    description = "Takes a prisoner id and return their current visit order balance, detailed version.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner balance returned.",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get prisoner balance.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Prisoner balance not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonerDetailedBalance(
    @Schema(description = "prisonerId", example = "AA123456", required = true)
    @PathVariable
    prisonerId: String,
  ): PrisonerDetailedBalanceDto = balanceService.getPrisonerDetailedBalance(prisonerId) ?: throw NotFoundException("Prisoner $prisonerId not found")

  @PreAuthorize("hasRole('$ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API')")
  @PutMapping(VO_BALANCE)
  @Operation(
    summary = "Endpoint to allow STAFF to manually adjust a prisoner's VO and / or PVO balance.",
    description = "Endpoint to allow STAFF to manually adjust a prisoner's VO and / or PVO balance.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner VO and / or PVO balance adjusted successfully.",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to adjust prisoner balance.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "422",
        description = "Adjust prisoner balance validation failed.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun adjustPrisonerVOBalance(
    @Schema(description = "prisonerId", example = "AA123456", required = true)
    @PathVariable
    prisonerId: String,
    @RequestBody
    balanceAdjustmentDto: PrisonerBalanceAdjustmentDto,
  ) = balanceService.adjustPrisonerBalance(prisonerId, balanceAdjustmentDto)
}
