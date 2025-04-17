package uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search

import com.fasterxml.jackson.annotation.JsonAlias
import io.swagger.v3.oas.annotations.media.Schema

data class PrisonerDto(
  @Schema(required = true, description = "Prisoner Number", example = "A1234AA")
  @JsonAlias("prisonerNumber")
  val prisonerId: String,

  @Schema(description = "Prison ID", example = "MDI")
  val prisonId: String,

  @Schema(description = "In / out status of prisoner", example = "IN")
  val inOutStatus: String,
)
