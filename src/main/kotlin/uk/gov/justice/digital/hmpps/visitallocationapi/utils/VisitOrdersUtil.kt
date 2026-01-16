package uk.gov.justice.digital.hmpps.visitallocationapi.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeRepaymentReason
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class VisitOrdersUtil {
  fun handleNegativeBalanceRepayment(totalVosToAdd: Int, negativeBalance: Int, prisoner: PrisonerDetails, type: VisitOrderType, visitOrders: MutableList<VisitOrder>, negativePaymentReason: NegativeRepaymentReason) {
    if (totalVosToAdd < negativeBalance) {
      // If the totalVosToAdd doesn't fully cover debt, then only repay what is possible.
      prisoner.negativeVisitOrders
        .filter { it.type == type && it.status == NegativeVisitOrderStatus.USED }
        .sortedBy { it.createdTimestamp }
        .take(totalVosToAdd)
        .forEach { negativeVisitOrder ->
          markNegativeVOAsRepaid(negativeVisitOrder, negativePaymentReason)
        }
    } else {
      // If the totalVosToAdd pushes the balance positive, repay all debt and generate the required amount of positive VO / PVOs.
      prisoner.negativeVisitOrders
        .filter { it.type == type && it.status == NegativeVisitOrderStatus.USED }
        .forEach { negativeVisitOrder ->
          markNegativeVOAsRepaid(negativeVisitOrder, negativePaymentReason)
        }

      val visitOrdersToCreate = totalVosToAdd - negativeBalance

      repeat(visitOrdersToCreate) {
        visitOrders.add(createAvailableVisitOrder(prisoner, type))
      }
    }
  }

  fun generateVos(prisoner: PrisonerDetails, totalVosToGenerate: Int, visitOrderType: VisitOrderType): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(totalVosToGenerate) {
      visitOrders.add(createAvailableVisitOrder(prisoner, visitOrderType))
    }

    return visitOrders
  }

  fun generateNegativeVos(prisoner: PrisonerDetails, totalNegativeVosToGenerate: Int, visitOrderType: VisitOrderType): List<NegativeVisitOrder> {
    val negativeVisitOrders = mutableListOf<NegativeVisitOrder>()
    repeat(totalNegativeVosToGenerate) {
      negativeVisitOrders.add(createNegativeVisitOrder(prisoner, visitOrderType))
    }

    return negativeVisitOrders
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

  private fun createNegativeVisitOrder(
    prisoner: PrisonerDetails,
    type: VisitOrderType,
    createdTimestamp: LocalDateTime = LocalDateTime.now(),
  ): NegativeVisitOrder = NegativeVisitOrder(
    type = type,
    status = NegativeVisitOrderStatus.USED,
    createdTimestamp = createdTimestamp,
    prisoner = prisoner,
  )

  private fun markNegativeVOAsRepaid(negativeVisitOrder: NegativeVisitOrder, negativePaymentReason: NegativeRepaymentReason) {
    negativeVisitOrder.status = NegativeVisitOrderStatus.REPAID
    negativeVisitOrder.repaidDate = LocalDate.now()
    negativeVisitOrder.repaidReason = negativePaymentReason
  }
}
