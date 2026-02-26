package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events

import jakarta.validation.constraints.NotBlank
import tools.jackson.databind.annotation.JsonDeserialize
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.deserializers.RawJsonDeserializer

data class DomainEvent(
  @field:NotBlank
  val eventType: String,

  @param:JsonDeserialize(using = RawJsonDeserializer::class)
  @field:NotBlank
  val additionalInformation: String,
)
