package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule

object TestObjectMapper {
  val mapper: ObjectMapper =
    JsonMapper.builder()
      .addModule(JavaTimeModule())
      .addModule(kotlinModule())
      .build()
}
