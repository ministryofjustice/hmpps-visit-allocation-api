package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo

import jakarta.validation.constraints.NotBlank

data class VisitCancelledInfo(
  @field:NotBlank
  val reference: String,

  val visitType: String? = null,
)
