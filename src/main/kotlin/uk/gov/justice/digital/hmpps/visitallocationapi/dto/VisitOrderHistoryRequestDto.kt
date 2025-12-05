package uk.gov.justice.digital.hmpps.visitallocationapi.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class VisitOrderHistoryRequestDto(
  @param:Schema(description = "Start date since when the visit history order records are needed", example = "2025-01-01", required = true)
  val fromDate: LocalDate,
)
