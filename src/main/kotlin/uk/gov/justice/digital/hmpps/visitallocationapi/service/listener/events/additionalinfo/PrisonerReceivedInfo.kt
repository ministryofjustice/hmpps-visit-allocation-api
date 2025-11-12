package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType

data class PrisonerReceivedInfo(
  @field:NotBlank
  @param:JsonProperty("nomsNumber")
  val prisonerId: String,

  @field:NotBlank
  @param:JsonProperty("prisonId")
  val prisonCode: String,

  @param:NotNull
  @param:JsonProperty("reason")
  val reason: PrisonerReceivedReasonType,
)
