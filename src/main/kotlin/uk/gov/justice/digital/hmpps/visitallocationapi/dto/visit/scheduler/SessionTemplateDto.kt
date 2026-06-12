package uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Session template")
class SessionTemplateDto(
  @param:Schema(description = "The type of visit order restriction", example = "PVO", required = true)
  val visitOrderRestriction: SessionTemplateVisitOrderRestrictionType,
)

enum class SessionTemplateVisitOrderRestrictionType {
  VO_PVO,
  VO,
  PVO,
  NONE,
}
