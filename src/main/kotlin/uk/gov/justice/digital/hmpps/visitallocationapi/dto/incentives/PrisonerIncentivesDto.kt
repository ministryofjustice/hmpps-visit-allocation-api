package uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonerIncentivesDto(
  @field:Schema(required = true, description = "Incentive level code", example = "STD")
  val iepCode: String,
)
