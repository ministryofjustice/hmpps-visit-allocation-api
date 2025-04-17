package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class PrisonerMergedInfo(
  @NotBlank
  @JsonProperty("nomsNumber")
  val prisonerId: String,

  @NotBlank
  @JsonProperty("removedNomsNumber")
  val removedPrisonerId: String,
)
