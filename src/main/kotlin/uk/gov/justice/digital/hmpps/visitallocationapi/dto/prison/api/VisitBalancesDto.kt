package uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Balances of visit orders and privilege visit orders")
data class VisitBalancesDto(
  @param:Schema(required = true, description = "Balance of visit orders remaining")
  val remainingVo: Int,

  @param:Schema(required = true, description = "Balance of privilege visit orders remaining")
  val remainingPvo: Int,

  @param:Schema(required = false, description = "Date of last IEP allocation")
  val latestIepAdjustDate: LocalDate?,

  @param:Schema(required = false, description = "Date of last Priv IEP allocation")
  val latestPrivIepAdjustDate: LocalDate?,
)
