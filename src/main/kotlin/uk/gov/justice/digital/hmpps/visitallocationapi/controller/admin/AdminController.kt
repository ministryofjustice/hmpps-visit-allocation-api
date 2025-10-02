package uk.gov.justice.digital.hmpps.visitallocationapi.controller.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__ADMIN
import uk.gov.justice.digital.hmpps.visitallocationapi.service.AdminService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

const val RESET_NEGATIVE_VO_BALANCE = "/admin/prison/{prisonCode}/reset"

@RestController
class BalanceController(val adminService: AdminService) {
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
      ApiResponse(
        responseCode = "404",
        description = "Prison code invalid",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun resetPrisonersNegativeBalance(
    @Schema(description = "prison code", example = "HEI", required = true)
    @PathVariable
    prisonCode: String,
  ) = adminService.resetPrisonerNegativeBalance(prisonCode)
}
