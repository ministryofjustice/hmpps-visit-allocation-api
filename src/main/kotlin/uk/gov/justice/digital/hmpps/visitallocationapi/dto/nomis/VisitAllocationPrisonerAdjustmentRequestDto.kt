package uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class VisitAllocationPrisonerAdjustmentRequestDto(
  @field:Schema(description = "nomsNumber of the prisoner", example = "AA123456", required = true)
  @field:NotNull
  @field:NotEmpty
  val prisonerId: String,

  @field:Schema(description = "The change log (adjustments) ID", example = "123456", required = true)
  @field:NotNull
  val changeLogId: Long,
)
