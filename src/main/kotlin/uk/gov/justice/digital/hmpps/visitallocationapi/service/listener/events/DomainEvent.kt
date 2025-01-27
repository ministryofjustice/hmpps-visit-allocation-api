package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.deserializers.RawJsonDeserializer

data class DomainEvent(
  @NotBlank
  val eventType: String,

  @JsonDeserialize(using = RawJsonDeserializer::class)
  @NotBlank
  val additionalInformation: String,
)
