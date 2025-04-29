package uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType

data class VisitAllocationPrisonerAdjustmentResponseDto(
  @Schema(description = "nomsNumber of the prisoner", example = "AA123456", required = true)
  val prisonerId: String,

  @Schema(description = "previous VO balance of prisoner (can be negative)", example = "2", required = false)
  val voBalance: Int?,

  @Schema(description = "change to previous VO balance (can be negative)", example = "-1", required = false)
  val changeToVoBalance: Int?,

  @Schema(description = "previous PVO balance of prisoner (can be negative)", example = "AA123456", required = false)
  val pvoBalance: Int?,

  @Schema(description = "change to previous PVO balance (can be negative)", example = "-1", required = false)
  val changeToPvoBalance: Int?,

  @Schema(description = "type of change applied", example = "SYNC", required = true)
  val changeLogType: ChangeLogType,

  @Schema(description = "additional information of change applied", example = "Gave prisoner extra VO", required = false)
  val comment: String?,
)
