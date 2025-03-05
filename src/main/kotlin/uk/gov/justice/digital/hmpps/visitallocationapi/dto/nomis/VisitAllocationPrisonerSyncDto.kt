package uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeSource
import java.time.LocalDate

data class VisitAllocationPrisonerSyncDto(
  @Schema(description = "nomsNumber of the prisoner", example = "AA123456", required = true)
  @field:NotNull
  @field:NotEmpty
  val prisonerId: String,

  @Schema(description = "The previous VO balance (can be negative)", example = "5", required = true)
  @field:NotNull
  val oldVoBalance: Int,

  @Schema(description = "The change of the VO balance (can be negative)", example = "5", required = false)
  @field:NotNull
  val changeToVoBalance: Int? = null,

  @Schema(description = "The previous PVO balance (can be negative)", example = "2", required = true)
  @field:NotNull
  val oldPvoBalance: Int,

  @Schema(description = "The change of the PVO balance (can be negative)", example = "5", required = false)
  @field:NotNull
  val changeToPvoBalance: Int? = null,

  @Schema(description = "The date which the change was made", example = "2025-02-28", required = true)
  @field:NotNull
  val createdDate: LocalDate,

  @Schema(description = "The reason for the adjustment", example = "VO_ISSUE", required = true)
  @field:NotNull
  val adjustmentReasonCode: AdjustmentReasonCode,

  @Schema(description = "The source of the change being made", example = "SYSTEM or STAFF", required = true)
  @field:NotNull
  val changeSource: ChangeSource,

  @Schema(description = "Additional information on the sync reason", example = "Manually adjusted for phone credit", required = true)
  @field:NotEmpty
  val comment: String? = null,
)
