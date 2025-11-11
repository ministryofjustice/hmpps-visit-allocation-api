package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import jakarta.validation.constraints.NotBlank

data class PrisonerBookingMovedInfo(
  @field:NotBlank
  val movedFromNomsNumber: String,

  @field:NotBlank
  val movedToNomsNumber: String,
)
