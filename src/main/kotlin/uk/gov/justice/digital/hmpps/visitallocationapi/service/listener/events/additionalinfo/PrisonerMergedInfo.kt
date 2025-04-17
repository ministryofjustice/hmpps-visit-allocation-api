package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import jakarta.validation.constraints.NotBlank

data class PrisonerMergedInfo(
  @NotBlank
  val nomsNumber: String,

  @NotBlank
  val removedNomsNumber: String,
)
