package uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search

import com.fasterxml.jackson.annotation.JsonAlias
import io.swagger.v3.oas.annotations.media.Schema

data class PrisonerDto(
  @field:Schema(required = true, description = "Prisoner Number", example = "A1234AA")
  @JsonAlias("prisonerNumber")
  val prisonerId: String,

  @field:Schema(description = "Prison ID", example = "MDI")
  val prisonId: String,

  @field:Schema(description = "In / out status of prisoner", example = "IN")
  val inOutStatus: String,

  @field:Schema(description = "Last prison ID of the prisoner", example = "IN")
  val lastPrisonId: String,

  @field:Schema(description = "Convicted Status", example = "Convicted", allowableValues = ["Convicted", "Remand"])
  val convictedStatus: String? = null,
)
