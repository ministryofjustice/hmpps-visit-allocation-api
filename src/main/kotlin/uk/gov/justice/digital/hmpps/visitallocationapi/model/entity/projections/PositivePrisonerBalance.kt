package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.projections

import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType

interface PositivePrisonerBalance {
  val type: VisitOrderType
  val balance: Int
}
