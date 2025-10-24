package uk.gov.justice.digital.hmpps.visitallocationapi.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerDetailedBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus.USED
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus.ACCUMULATED
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus.AVAILABLE
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType.PVO
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType.VO
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import java.time.LocalDate

@Component
class VOBalancesUtil {
  companion object {
    const val VO_ALLOCATION_DAYS = 14L
    const val PVO_ALLOCATION_DAYS = 28L
  }

  fun getPrisonersDetailedBalance(prisonerDetails: PrisonerDetails): PrisonerDetailedBalanceDto {
    val availableVos = getAvailableVOBalance(prisonerDetails)
    val accumulatedVos = getAccumulatedVOBalance(prisonerDetails)
    val negativeVos = getNegativeVOBalance(prisonerDetails)
    val availablePvos = getAvailablePVOBalance(prisonerDetails)
    val negativePvos = getNegativePVOBalance(prisonerDetails)
    val nextVOAllocationDate = getNextVOAllocationDate(prisonerDetails.lastVoAllocatedDate)

    return PrisonerDetailedBalanceDto(
      prisonerId = prisonerDetails.prisonerId,
      availableVos = availableVos,
      accumulatedVos = accumulatedVos,
      negativeVos = negativeVos,
      voBalance = prisonerDetails.getVoBalance(),
      availablePvos = availablePvos,
      negativePvos = negativePvos,
      pvoBalance = prisonerDetails.getPvoBalance(),
      lastVoAllocatedDate = prisonerDetails.lastVoAllocatedDate,
      nextVoAllocationDate = nextVOAllocationDate,
      lastPvoAllocatedDate = prisonerDetails.lastPvoAllocatedDate,
      nextPvoAllocationDate = getNextPvoAllocationDate(prisonerDetails.lastPvoAllocatedDate, nextVOAllocationDate),
    )
  }

  private fun getAvailableVOBalance(prisonerDetails: PrisonerDetails) = prisonerDetails.visitOrders.count { it.type == VO && it.status == AVAILABLE }

  private fun getAccumulatedVOBalance(prisonerDetails: PrisonerDetails) = prisonerDetails.visitOrders.count { it.type == VO && it.status == ACCUMULATED }

  private fun getNegativeVOBalance(prisonerDetails: PrisonerDetails) = prisonerDetails.negativeVisitOrders.count { it.type == VO && it.status == USED }

  private fun getAvailablePVOBalance(prisonerDetails: PrisonerDetails) = prisonerDetails.visitOrders.count { it.type == PVO && it.status == AVAILABLE }

  private fun getNegativePVOBalance(prisonerDetails: PrisonerDetails) = prisonerDetails.negativeVisitOrders.count { it.type == PVO && it.status == USED }

  private fun getNextVOAllocationDate(lastVoAllocatedDate: LocalDate): LocalDate {
    var nextVoAllocationDate = lastVoAllocatedDate.plusDays(VO_ALLOCATION_DAYS)

    // if the next allocation date is in the past - set the next allocation date to tomorrow
    if (nextVoAllocationDate <= LocalDate.now()) {
      nextVoAllocationDate = LocalDate.now().plusDays(1)
    }

    // if the lastVoAllocatedDate is in the future (bad data scenario), set it to the same as the last allocation date
    if (lastVoAllocatedDate > LocalDate.now()) {
      nextVoAllocationDate = lastVoAllocatedDate
    }

    return nextVoAllocationDate
  }

  private fun getNextPvoAllocationDate(lastPvoAllocatedDate: LocalDate?, nextVOAllocationDate: LocalDate): LocalDate? {
    // if the lastVoAllocatedDate is in the future (bad data scenario), return last allocation date
    if (lastPvoAllocatedDate != null && lastPvoAllocatedDate > LocalDate.now()) {
      return lastPvoAllocatedDate
    }

    var nextPvoAllocationDate = lastPvoAllocatedDate?.plusDays(PVO_ALLOCATION_DAYS)

    // if the calculated next allocation date is null or in the in the past - set the next allocation date to nextVOAllocationDate
    if (nextPvoAllocationDate == null || nextPvoAllocationDate <= LocalDate.now()) {
      nextPvoAllocationDate = nextVOAllocationDate
    } // if the nextPvoAllocationDate is in the future but nextPvoAllocationDate is before nextVOAllocationDate set it to nextVOAllocationDate
    else if (nextPvoAllocationDate > LocalDate.now()) {
      if (nextPvoAllocationDate.isBefore(nextVOAllocationDate)) {
        nextPvoAllocationDate = nextVOAllocationDate
      }
    }

    return nextPvoAllocationDate
  }
}
