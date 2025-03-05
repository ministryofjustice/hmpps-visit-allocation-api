package uk.gov.justice.digital.hmpps.visitallocationapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

const val VO_MIGRATION: String = "/visits/allocation/migrate"

@RestController
class NomisSyncController {
  @PreAuthorize("hasRole('VISIT_ALLOCATION_MIGRATION')")
  @PostMapping(VO_MIGRATION)
  @Operation(
    summary = "Endpoint to migrate prisoner VO / PVO balances from NOMIS to DPS.",
    description = "Takes a list of prisoners and 'onboards' them onto DPS, syncing their balance with NOMIS.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner information has been migrated.",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to migrate prisoner VO / PVO information.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun migratePrisonerVisitOrders(@RequestBody @Valid visitAllocationPrisonerMigrationDto: VisitAllocationPrisonerMigrationDto): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.OK).build()
}
