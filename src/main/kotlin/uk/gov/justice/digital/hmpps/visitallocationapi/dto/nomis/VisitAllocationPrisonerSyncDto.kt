package uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import java.time.LocalDate

data class VisitAllocationPrisonerSyncDto(
  @field:Schema(description = "nomsNumber of the prisoner", example = "AA123456", required = true)
  @field:NotNull
  @field:NotEmpty
  val prisonerId: String,

  @field:Schema(description = "The previous VO balance (can be negative)", example = "5", required = false)
  val oldVoBalance: Int? = null,

  @field:Schema(description = "The change of the VO balance (can be negative)", example = "5", required = false)
  val changeToVoBalance: Int? = null,

  @field:Schema(description = "The previous PVO balance (can be negative)", example = "2", required = false)
  val oldPvoBalance: Int? = null,

  @field:Schema(description = "The change of the PVO balance (can be negative)", example = "5", required = false)
  val changeToPvoBalance: Int? = null,

  @field:Schema(description = "The date which the change was made", example = "2025-02-28", required = true)
  @field:NotNull
  val createdDate: LocalDate,

  @field:Schema(description = "The reason for the adjustment", example = "VO_ISSUE", required = true)
  @field:NotNull
  val adjustmentReasonCode: AdjustmentReasonCode,

  @field:Schema(description = "The source of the change being made", example = "SYSTEM or STAFF", required = true)
  @field:NotNull
  val changeLogSource: ChangeLogSource,

  @field:Schema(description = "Additional information on the sync reason", example = "Manually adjusted for phone credit", required = false)
  val comment: String? = null,
)
