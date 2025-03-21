package uk.gov.justice.digital.hmpps.visitallocationapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.visitallocationapi.service.BalanceService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

const val VO_BALANCE = "/visits/allocation/prisoner/{prisonerId}/balance"

@RestController
class BalanceController(val balanceService: BalanceService) {
  @PreAuthorize("hasRole('ROLE_VISIT_ALLOCATION_API__NOMIS_API')")
  @GetMapping(VO_BALANCE)
  @Operation(
    summary = "Endpoint to get a prisoners current balance. Returns 0 if prisoner not found",
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
}
