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

  @Schema(description = "Last prison ID of the prisoner", example = "IN")
  val lastPrisonId: String,

  @Schema(description = "Convicted Status", example = "Convicted", allowableValues = ["Convicted", "Remand"])
  val convictedStatus: String? = null,

  @Schema(description = "The current incentive level of the prisoner")
  val currentIncentive: CurrentIncentive,
)

data class CurrentIncentive(
  val level: Level,
)

data class Level(
  val code: String,
)
