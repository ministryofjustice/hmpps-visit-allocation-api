package uk.gov.justice.digital.hmpps.visitallocationapi.controller.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__ADMIN
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.admin.PrisonNegativeBalanceCountDto
import uk.gov.justice.digital.hmpps.visitallocationapi.service.AdminService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

const val BASE_ENDPOINT = "/admin/prison/{prisonCode}"
const val RESET_NEGATIVE_VO_BALANCE = "$BASE_ENDPOINT/reset"
const val RESET_NEGATIVE_VO_BALANCE_COUNT = "$RESET_NEGATIVE_VO_BALANCE/count"

@RestController
class AdminController(val adminService: AdminService) {
  @PreAuthorize("hasRole('$ROLE_VISIT_ALLOCATION_API__ADMIN')")
  @PostMapping(RESET_NEGATIVE_VO_BALANCE)
  @Operation(
    summary = "Endpoint to allow an admin to reset all prisoners negative balance to zero in a given prison.",
    description = "Takes a prison id and repays all of their prisoners negative balance, leaving positive balance uneffected.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoners negative balances reset.",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to reset prisoners balances.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun resetPrisonersNegativeBalance(
    @Schema(description = "prison code", example = "HEI", required = true)
    @PathVariable
    prisonCode: String,
  ) = adminService.resetPrisonerNegativeBalance(prisonCode)

  @PreAuthorize("hasRole('$ROLE_VISIT_ALLOCATION_API__ADMIN')")
  @GetMapping(RESET_NEGATIVE_VO_BALANCE_COUNT)
  @Operation(
    summary = "Endpoint to get the count of prisoners with a negative balance in a given prison.",
    description = "Takes a prison id and returns the count of prisoners with a negative balance.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Count returned.",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get count.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonNegativePrisonerBalanceCount(
    @Schema(description = "prison code", example = "HEI", required = true)
    @PathVariable
    prisonCode: String,
  ): PrisonNegativeBalanceCountDto = adminService.getPrisonPrisonerNegativeBalanceCount(prisonCode)
}
