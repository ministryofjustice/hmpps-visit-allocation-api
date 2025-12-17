package uk.gov.justice.digital.hmpps.visitallocationapi.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryAttributeType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistoryAttributes

data class VisitOrderHistoryAttributesDto(
  @param:Schema(description = "Visit order history attribute type", example = "VISIT_REFERENCE", required = true)
  val attributeType: VisitOrderHistoryAttributeType,

  @param:Schema(description = "Visit order history attribute value", required = true)
  val attributeValue: String,
) {
  constructor(visitOrderHistoryAttributes: VisitOrderHistoryAttributes) : this(
    attributeType = visitOrderHistoryAttributes.attributeType,
    attributeValue = visitOrderHistoryAttributes.attributeValue,
  )
}
