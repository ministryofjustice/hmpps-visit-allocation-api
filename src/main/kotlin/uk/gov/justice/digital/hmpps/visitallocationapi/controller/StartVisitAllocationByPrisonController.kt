package uk.gov.justice.digital.hmpps.visitallocationapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.jobs.VisitAllocationEventJobDto
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitAllocationByPrisonService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

const val VO_START_VISIT_ALLOCATION_JOB: String = "/visits/allocation/job/start"

@RestController
class StartVisitAllocationByPrisonController(
  private val visitAllocationByPrisonService: VisitAllocationByPrisonService,
) {
  // This endpoint is secured in the ingress rather than the app so that they can be called from
  // within the namespace without requiring authentication
  @PostMapping(VO_START_VISIT_ALLOCATION_JOB)
  @Operation(
    summary = "Endpoint to trigger adding prisons enabled for VO allocation to allocations queue.",
    description = "Endpoint to trigger adding prisons enabled for VO allocation to allocations queue.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Active prisons for VO allocation added to queue.",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to trigger adding active prisons for VO allocation to queue",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun triggerVisitAllocationForActivePrisons(): ResponseEntity<VisitAllocationEventJobDto> {
    val visitAllocationEventJobDto = visitAllocationByPrisonService.triggerVisitAllocationForActivePrisons()
    return ResponseEntity.status(HttpStatus.OK).body(visitAllocationEventJobDto)
  }
}
