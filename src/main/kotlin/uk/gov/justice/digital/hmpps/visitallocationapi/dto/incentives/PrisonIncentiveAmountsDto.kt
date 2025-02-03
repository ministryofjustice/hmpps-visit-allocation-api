package uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonIncentiveAmountsDto(
  @Schema(required = true, description = "Incentive level code", example = "STD")
  val levelCode: String,

  @Schema(required = true, description = "Incentive bonus for visit orders", example = "1")
  val visitOrders: Int,

  @Schema(required = true, description = "Incentive bonus for privileged visit orders", example = "2")
  val privilegedVisitOrders: Int,
)
