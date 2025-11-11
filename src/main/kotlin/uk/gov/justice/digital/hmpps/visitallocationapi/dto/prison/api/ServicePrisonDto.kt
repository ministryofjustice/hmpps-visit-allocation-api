package uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Balances of visit orders and privilege visit orders")
data class ServicePrisonDto(
  @param:Schema(description = "Prison Code", example = "MDI", required = true)
  val agencyId: String,
)
