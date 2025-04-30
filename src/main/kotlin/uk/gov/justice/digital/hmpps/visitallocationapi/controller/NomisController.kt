package uk.gov.justice.digital.hmpps.visitallocationapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__NOMIS_API
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerAdjustmentRequestDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerAdjustmentResponseDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisMigrationService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

const val VO_NOMIS = "/visits/allocation/prisoner"

const val VO_PRISONER_MIGRATION: String = "$VO_NOMIS/migrate"
const val VO_PRISONER_SYNC: String = "$VO_NOMIS/sync"

const val VO_GET_PRISONER_ADJUSTMENT = "$VO_NOMIS/{prisonerId}/adjustments/{adjustmentId}"

@RestController
class NomisController(
  val nomisMigrationService: NomisMigrationService,
  val nomisSyncService: NomisSyncService,
) {
  @PreAuthorize("hasRole('$ROLE_VISIT_ALLOCATION_API__NOMIS_API')")
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
  fun migratePrisonerVisitOrders(@RequestBody @Valid visitAllocationPrisonerMigrationDto: VisitAllocationPrisonerMigrationDto): ResponseEntity<Void> {
    nomisMigrationService.migratePrisoner(visitAllocationPrisonerMigrationDto)
    return ResponseEntity.status(HttpStatus.OK).build()
  }

  @PreAuthorize("hasRole('$ROLE_VISIT_ALLOCATION_API__NOMIS_API')")
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
  fun syncPrisonerVisitOrders(@RequestBody @Valid visitAllocationPrisonerSyncDto: VisitAllocationPrisonerSyncDto): ResponseEntity<Void> {
    nomisSyncService.syncPrisonerAdjustmentChanges(visitAllocationPrisonerSyncDto)
    return ResponseEntity.status(HttpStatus.OK).build()
  }

  @PreAuthorize("hasRole('$ROLE_VISIT_ALLOCATION_API__NOMIS_API')")
  @GetMapping(VO_GET_PRISONER_ADJUSTMENT)
  @Operation(
    summary = "Endpoint to get adjustment for a prisoner, given their ID and adjustment id (changeLogId).",
    description = "Returns a snapshot of prisoners balance (including changes and reason for change).",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner information returned.",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get prisoner VO / PVO information.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "No adjustments found in visit allocation api for given prisoner.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonerAdjustment(
    @Schema(description = "prisonerId", example = "AA123456", required = true)
    @PathVariable
    prisonerId: String,
    @Schema(description = "adjustmentId", example = "1234", required = true)
    @PathVariable
    adjustmentId: String,
  ): VisitAllocationPrisonerAdjustmentResponseDto = nomisSyncService.getChangeLogForNomis(VisitAllocationPrisonerAdjustmentRequestDto(prisonerId, adjustmentId.toLong()))
}
