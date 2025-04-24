package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import jakarta.validation.constraints.NotBlank

data class PrisonerBookingMovedInfo(
  @NotBlank
  val movedFromNomsNumber: String,

  @NotBlank
  val movedToNomsNumber: String,
)
