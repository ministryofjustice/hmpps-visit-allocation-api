package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper

object TestObjectMapper {
  val mapper: ObjectMapper = jacksonObjectMapper()
}
