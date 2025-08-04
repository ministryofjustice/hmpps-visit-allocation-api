package uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Visit")
@JsonInclude(JsonInclude.Include.NON_NULL)
class VisitDto(
  @field:Schema(description = "Visit Reference", example = "v9-d7-ed-7u", required = true)
  val reference: String,
  @field:Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  val prisonerId: String,
  @JsonProperty("prisonId")
  @JsonAlias("prisonCode")
  @field:Schema(description = "Prison Id", example = "MDI", required = true)
  val prisonCode: String,
)
