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
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

const val VO_NOMIS = "/visits/allocation/prisoner"
const val VO_PRISONER_MIGRATION: String = "$VO_NOMIS/migrate"
const val VO_PRISONER_SYNC: String = "$VO_NOMIS/sync"

@RestController
class NomisSyncController {
  @PreAuthorize("hasRole('ROLE_VISIT_ALLOCATION_API__NOMIS_API')")
  @PostMapping(VO_PRISONER_MIGRATION)
  @Operation(
    summary = "Endpoint to migrate prisoner VO / PVO balances from NOMIS to DPS.",
    description = "Takes a prisoner and 'onboards' them onto DPS, syncing their balance with NOMIS.",
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

  @PreAuthorize("hasRole('ROLE_VISIT_ALLOCATION_API__NOMIS_API')")
  @PostMapping(VO_PRISONER_SYNC)
  @Operation(
    summary = "Endpoint to sync ongoing changes to prisoner VO / PVO balances from NOMIS to DPS.",
    description = "Takes a set of changes to a prisoners and syncs them onto DPS.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner information has been synced.",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to sync prisoner VO / PVO information.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun syncPrisonerVisitOrders(@RequestBody @Valid visitAllocationPrisonerSyncDto: VisitAllocationPrisonerSyncDto): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.OK).build()
}
