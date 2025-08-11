package uk.gov.justice.digital.hmpps.visitallocationapi.dto.snapshots

import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate
import java.time.LocalDateTime

// NOTICE: Used to track changes made during allocation / sync, to determine if a change_log entry should be generated.

data class VOSnap(
  val id: Long?,
  val type: VisitOrderType,
  val status: VisitOrderStatus,
  val created: LocalDateTime,
  val expiry: LocalDate?,
  val visitRef: String?,
)

data class NVOSnap(
  val id: Long?,
  val type: VisitOrderType,
  val status: NegativeVisitOrderStatus,
  val created: LocalDateTime,
  val repaid: LocalDate?,
)

data class PrisonerSnap(
  val prisonerId: String,
  val lastVoAllocationDate: LocalDate,
  val lastPvoAllocationDate: LocalDate?,
  val vos: List<VOSnap>,
  val nvos: List<NVOSnap>,
)

// Extension mappers (call these inside a transaction so LAZY loads are fine)
fun VisitOrder.toSnap() = VOSnap(id, type, status, createdTimestamp, expiryDate, visitReference)

fun NegativeVisitOrder.toSnap() = NVOSnap(id, type, status, createdTimestamp, repaidDate)

fun PrisonerDetails.snapshot() = PrisonerSnap(
  prisonerId,
  lastVoAllocatedDate,
  lastPvoAllocatedDate,
  visitOrders.map { it.toSnap() },
  negativeVisitOrders.map { it.toSnap() },
)
