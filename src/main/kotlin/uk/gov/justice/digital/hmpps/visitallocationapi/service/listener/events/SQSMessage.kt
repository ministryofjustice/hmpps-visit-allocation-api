package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class SQSMessage(
  @field:NotBlank
  @JsonProperty("Type")
  val type: String,
  @field:NotBlank
  @JsonProperty("Message")
  val message: String,
  @JsonProperty("MessageId")
  val messageId: String? = null,
)
