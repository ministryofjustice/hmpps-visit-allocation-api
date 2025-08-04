package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType

data class PrisonerReceivedInfo(
  @field:NotBlank
  @JsonProperty("nomsNumber")
  val prisonerId: String,

  @field:NotBlank
  @JsonProperty("prisonId")
  val prisonCode: String,

  @NotNull
  @JsonProperty("reason")
  val reason: PrisonerReceivedReasonType,
)
