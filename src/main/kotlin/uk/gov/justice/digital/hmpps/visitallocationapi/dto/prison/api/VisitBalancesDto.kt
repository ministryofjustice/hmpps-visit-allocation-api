package uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Balances of visit orders and privilege visit orders")
data class VisitBalancesDto(
  @field:Schema(required = true, description = "Balance of visit orders remaining")
  val remainingVo: Int,

  @field:Schema(required = true, description = "Balance of privilege visit orders remaining")
  val remainingPvo: Int,

  @field:Schema(required = false, description = "Date of last IEP allocation")
  val latestIepAdjustDate: LocalDate?,

  @field:Schema(required = false, description = "Date of last Priv IEP allocation")
  val latestPrivIepAdjustDate: LocalDate?,
)
