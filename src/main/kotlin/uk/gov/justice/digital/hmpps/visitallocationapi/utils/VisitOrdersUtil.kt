package uk.gov.justice.digital.hmpps.visitallocationapi.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDateTime

@Component
class VisitOrdersUtil {

  fun generateVos(prisoner: PrisonerDetails, totalVosToGenerate: Int, visitOrderType: VisitOrderType): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(totalVosToGenerate) {
      visitOrders.add(createAvailableVisitOrder(prisoner, visitOrderType))
    }

    return visitOrders
  }
  fun createAvailableVisitOrder(
    prisoner: PrisonerDetails,
    type: VisitOrderType,
    createdTimestamp: LocalDateTime = LocalDateTime.now(),
  ): VisitOrder = VisitOrder(
    type = type,
    status = VisitOrderStatus.AVAILABLE,
    createdTimestamp = createdTimestamp,
    expiryDate = null,
    prisoner = prisoner,
  )
}
