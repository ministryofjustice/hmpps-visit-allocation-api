package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.deserializers

import tools.jackson.core.JacksonException
import tools.jackson.databind.ValueDeserializer
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException

class RawJsonDeserializer : ValueDeserializer<String>() {
  @Throws(IOException::class, JacksonException::class)
  override fun deserialize(jp: tools.jackson.core.JsonParser, ctxt: tools.jackson.databind.DeserializationContext): String {
    val mapper = jacksonObjectMapper()
    val node = jp.readValueAsTree<tools.jackson.databind.JsonNode>()
    return mapper.writeValueAsString(node)
  }
}
