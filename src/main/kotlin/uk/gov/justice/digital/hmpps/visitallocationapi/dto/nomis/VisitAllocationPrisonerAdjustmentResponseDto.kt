package uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import java.time.LocalDateTime

data class VisitAllocationPrisonerAdjustmentResponseDto(
  @Schema(description = "nomsNumber of the prisoner", example = "AA123456", required = true)
  val prisonerId: String,

  @Schema(description = "previous VO balance of prisoner (can be negative)", example = "2", required = false)
  val voBalance: Int?,

  @Schema(description = "change to previous VO balance (can be negative)", example = "-1", required = false)
  val changeToVoBalance: Int?,

  @Schema(description = "previous PVO balance of prisoner (can be negative)", example = "1", required = false)
  val pvoBalance: Int?,

  @Schema(description = "change to previous PVO balance (can be negative)", example = "-1", required = false)
  val changeToPvoBalance: Int?,

  @Schema(description = "type of change applied", example = "SYNC", required = true)
  val changeLogType: ChangeLogType,

  @Schema(description = "user who applied change [If system -> SYSTEM, if user -> username]", example = "JSMITH", required = true)
  val userId: String,

  @Schema(description = "source of change applied", example = "'STAFF' / 'SYSTEM'", required = true)
  val changeLogSource: ChangeLogSource,

  @Schema(description = "Timestamp of when change occurred", required = true)
  val changeTimestamp: LocalDateTime,

  @Schema(description = "additional information of change applied", example = "Gave prisoner extra VO", required = false)
  val comment: String?,
)
