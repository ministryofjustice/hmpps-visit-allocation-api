package uk.gov.justice.digital.hmpps.visitallocationapi.dto.admin

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonNegativeBalanceCountDto(
  @param:Schema(description = "code of the prison", example = "HEI", required = true)
  val prisonCode: String,

  @param:Schema(description = "The count of all prisoners with a negative balance for given prison", example = "5", required = true)
  val count: Long,
)
