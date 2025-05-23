package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import jakarta.validation.constraints.NotBlank

data class VisitCancelledInfo(
  @NotBlank
  val reference: String,
)
