package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReleasedReasonType

data class PrisonerReleasedInfo(
  @NotBlank
  @JsonProperty("nomsNumber")
  val prisonerId: String,

  @NotBlank
  @JsonProperty("prisonId")
  val prisonCode: String,

  @NotBlank
  @JsonProperty("reason")
  val reasonType: PrisonerReleasedReasonType,
)
