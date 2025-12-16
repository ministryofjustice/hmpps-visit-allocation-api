package uk.gov.justice.digital.hmpps.visitallocationapi.utils

import uk.gov.justice.digital.hmpps.visitallocationapi.dto.snapshots.NVOSnap
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.snapshots.PrisonerSnap
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.snapshots.VOSnap
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.snapshots.snapshot
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus.REPAID
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus.ACCUMULATED
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus.AVAILABLE
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus.EXPIRED
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType.PVO
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType.VO
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails

class PrisonerChangeTrackingUtil {
  companion object {

    fun hasChangeOccurred(before: PrisonerSnap, afterEntity: PrisonerDetails): Boolean {
      val after = afterEntity.snapshot()
      if (before.prisonerId != after.prisonerId) return true
      if (before.lastVoAllocationDate != after.lastVoAllocationDate) return true
      if (before.lastPvoAllocationDate != after.lastPvoAllocationDate) return true
      return changedVos(before.vos, after.vos) || changedNvos(before.nvos, after.nvos)
    }

    fun hasAccumulationOccurred(before: PrisonerSnap, afterEntity: PrisonerDetails): Boolean {
      val beforeAccumulatedVosCount = before.vos.count { it.type == VO && it.status == ACCUMULATED }
      val afterAccumulatedVosCount = afterEntity.visitOrders.count { it.type == VO && it.status == ACCUMULATED }
      return beforeAccumulatedVosCount != afterAccumulatedVosCount
    }

    fun hasVoAllocationOccurred(before: PrisonerSnap, afterEntity: PrisonerDetails): Boolean {
      val beforeAvailableVosCount = before.vos.count { it.type == VO && it.status == AVAILABLE }
      val afterAvailableVosCount = afterEntity.visitOrders.count { it.type == VO && it.status == AVAILABLE }

      val beforeRepaidNVosCount = before.nvos.count { it.type == VO && it.status == REPAID }
      val afterRepaidNVosCount = afterEntity.negativeVisitOrders.count { it.type == VO && it.status == REPAID }

      return (beforeAvailableVosCount != afterAvailableVosCount || beforeRepaidNVosCount != afterRepaidNVosCount)
    }

    fun hasPVoAllocationOccurred(before: PrisonerSnap, afterEntity: PrisonerDetails): Boolean {
      val beforeAvailablePVosCount = before.vos.count { it.type == PVO && it.status == AVAILABLE }
      val afterAvailablePVosCount = afterEntity.visitOrders.count { it.type == PVO && it.status == AVAILABLE }

      val beforeRepaidNPVosCount = before.nvos.count { it.type == PVO && it.status == REPAID }
      val afterRepaidNPVosCount = afterEntity.negativeVisitOrders.count { it.type == PVO && it.status == REPAID }

      return (beforeAvailablePVosCount != afterAvailablePVosCount || beforeRepaidNPVosCount != afterRepaidNPVosCount)
    }

    fun hasVoExpirationOccurred(before: PrisonerSnap, afterEntity: PrisonerDetails): Boolean {
      val beforeExpiredVosCount = before.vos.count { it.type == VO && it.status == EXPIRED }
      val afterExpiredVosCount = afterEntity.visitOrders.count { it.type == VO && it.status == EXPIRED }

      return (beforeExpiredVosCount != afterExpiredVosCount)
    }

    fun hasPVoExpirationOccurred(before: PrisonerSnap, afterEntity: PrisonerDetails): Boolean {
      val beforeExpiredPVosCount = before.vos.count { it.type == PVO && it.status == EXPIRED }
      val afterExpiredPVosCount = afterEntity.visitOrders.count { it.type == PVO && it.status == EXPIRED }

      return (beforeExpiredPVosCount != afterExpiredPVosCount)
    }

    private fun changedVos(before: List<VOSnap>, after: List<VOSnap>): Boolean {
      fun key(v: VOSnap) = v.id ?: (v.type to v.created)
      if (before.size != after.size) return true
      val afterMap = after.associateBy(::key)
      for (b in before) {
        val a = afterMap[key(b)] ?: return true
        if (a.status != b.status || a.expiry != b.expiry || a.visitRef != b.visitRef) return true
      }
      return false
    }

    private fun changedNvos(before: List<NVOSnap>, after: List<NVOSnap>): Boolean {
      fun key(n: NVOSnap) = n.id ?: (n.type to n.created)
      if (before.size != after.size) return true
      val afterMap = after.associateBy(::key)
      for (b in before) {
        val a = afterMap[key(b)] ?: return true
        if (a.status != b.status || a.repaid != b.repaid) return true
      }
      return false
    }
  }
}
