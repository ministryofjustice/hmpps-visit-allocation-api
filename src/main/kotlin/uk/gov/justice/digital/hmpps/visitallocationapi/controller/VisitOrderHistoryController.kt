package uk.gov.justice.digital.hmpps.visitallocationapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.VisitOrderHistoryDto
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitOrderHistoryService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate

const val GET_VISIT_ORDER_HISTORY = "$PRISONER_ID_PATH/visit-order-history"

@RestController
class VisitOrderHistoryController(val visitOrderHistoryService: VisitOrderHistoryService) {
  @PreAuthorize("hasRole('$ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API')")
  @GetMapping(GET_VISIT_ORDER_HISTORY)
  @Operation(
    summary = "Endpoint to get visit order history for a prisoner.",
    description = "Returns visit order history for a prisoner on or after supplied fromDate.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner visit order history returned from the date supplied, empty list if no history.",
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
    ],
  )
  fun getPrisonerVisitOrderHistory(
    @Schema(description = "prisonerId", example = "AA123456", required = true)
    @PathVariable
    prisonerId: String,
    @RequestParam
    fromDate: LocalDate,
  ): List<VisitOrderHistoryDto> = visitOrderHistoryService.getVisitOrderHistoryForPrisoner(prisonerId, fromDate)
}
