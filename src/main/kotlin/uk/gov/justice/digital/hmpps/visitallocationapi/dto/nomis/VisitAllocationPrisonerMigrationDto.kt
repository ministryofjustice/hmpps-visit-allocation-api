package uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class VisitAllocationPrisonerMigrationDto(
  @field:Schema(description = "nomsNumber of the prisoner", example = "AA123456", required = true)
  @field:NotNull
  @field:NotEmpty
  val prisonerId: String,

  @field:Schema(description = "The current VO balance (can be negative)", example = "5", required = true)
  @field:NotNull
  val voBalance: Int,

  @field:Schema(description = "The current PVO balance (can be negative)", example = "2", required = true)
  @field:NotNull
  val pvoBalance: Int,

  @field:Schema(description = "The date which the last iep allocation was given", example = "2025-02-28", required = false)
  var lastVoAllocationDate: LocalDate? = null,
)
