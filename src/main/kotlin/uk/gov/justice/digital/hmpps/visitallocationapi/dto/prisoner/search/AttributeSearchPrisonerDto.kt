package uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search

import com.fasterxml.jackson.annotation.JsonAlias
import io.swagger.v3.oas.annotations.media.Schema

data class AttributeSearchPrisonerDto(
  @param:Schema(required = true, description = "Prisoner Number", example = "A1234AA")
  @param:JsonAlias("prisonerNumber")
  val prisonerId: String,
)
