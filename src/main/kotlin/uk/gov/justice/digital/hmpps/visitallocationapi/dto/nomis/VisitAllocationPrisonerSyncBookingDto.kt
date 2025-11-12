package uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

@Schema(description = "DTO to provide information of 2 prisoners effected by a booking moved between them in NOMIS")
data class VisitAllocationPrisonerSyncBookingDto(
  @param:Schema(description = "nomsNumber of the first prisoner", example = "AA123456", required = true)
  @field:NotNull
  @field:NotEmpty
  val firstPrisonerId: String,

  @param:Schema(description = "The new VO balance of the first prisoner (can be negative)", example = "5", required = true)
  val firstPrisonerVoBalance: Int,

  @param:Schema(description = "The new PVO balance of the first prisoner (can be negative)", example = "1", required = true)
  val firstPrisonerPvoBalance: Int,

  @param:Schema(description = "nomsNumber of the second prisoner", example = "AA123456", required = true)
  @field:NotNull
  @field:NotEmpty
  val secondPrisonerId: String,

  @param:Schema(description = "The new VO balance of the second prisoner (can be negative)", example = "5", required = true)
  val secondPrisonerVoBalance: Int,

  @param:Schema(description = "The new PVO balance of the second prisoner (can be negative)", example = "1", required = true)
  val secondPrisonerPvoBalance: Int,
)
