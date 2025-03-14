package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.projections

import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType

interface NegativePrisonerBalance {
  val type: NegativeVisitOrderType
  val balance: Int
}
