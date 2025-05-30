package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class PrisonerConvictionStatusChangedInfo(
  @NotBlank
  @JsonProperty("nomsNumber")
  val prisonerId: String,

  @NotBlank
  val convictedStatus: String? = null,
)
