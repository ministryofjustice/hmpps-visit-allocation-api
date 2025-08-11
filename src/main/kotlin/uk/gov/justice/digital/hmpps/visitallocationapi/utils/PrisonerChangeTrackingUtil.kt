package uk.gov.justice.digital.hmpps.visitallocationapi.utils

import uk.gov.justice.digital.hmpps.visitallocationapi.dto.snapshots.NVOSnap
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.snapshots.PrisonerSnap
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.snapshots.VOSnap
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.snapshots.snapshot
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
