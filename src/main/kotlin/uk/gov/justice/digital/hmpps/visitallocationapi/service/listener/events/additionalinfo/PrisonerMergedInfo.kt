package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class PrisonerMergedInfo(
  @field:NotBlank
  @param:JsonProperty("nomsNumber")
  val prisonerId: String,

  @field:NotBlank
  @param:JsonProperty("removedNomsNumber")
  val removedPrisonerId: String,
)
