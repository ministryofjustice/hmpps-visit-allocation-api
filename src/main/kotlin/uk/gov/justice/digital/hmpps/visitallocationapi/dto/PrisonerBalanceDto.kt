package uk.gov.justice.digital.hmpps.visitallocationapi.dto

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonerBalanceDto(
  @Schema(description = "nomsNumber of the prisoner", example = "AA123456", required = true)
  val prisonerId: String,

  @Schema(description = "The current VO balance (can be negative)", example = "5", required = true)
  val voBalance: Int,

  @Schema(description = "The current PVO balance (can be negative)", example = "2", required = true)
  val pvoBalance: Int,
)
