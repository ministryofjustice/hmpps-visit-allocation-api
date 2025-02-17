package uk.gov.justice.digital.hmpps.visitallocationapi.dto.jobs

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

data class VisitAllocationEventJobDto(
  @Schema(description = "Visit Allocation Job Reference", example = "aa-bb-cc-dd", required = true)
  @field:NotNull
  val allocationJobReference: String,

  @Schema(description = "Number of active prisons", example = "12", required = true)
  @field:NotNull
  val totalActivePrisons: Int = 0,
)
