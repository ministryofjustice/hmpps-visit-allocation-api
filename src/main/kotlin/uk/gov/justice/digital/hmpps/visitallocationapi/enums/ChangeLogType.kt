package uk.gov.justice.digital.hmpps.visitallocationapi.enums

@Suppress("unused")
enum class ChangeLogType {
  MIGRATION,
  SYNC,
  PRISONER_REMOVED,
  BATCH_PROCESS,
  ALLOCATION_USED_BY_VISIT,
  ALLOCATION_REFUNDED_BY_VISIT_CANCELLED,
  ALLOCATION_ADDED_AFTER_PRISONER_MERGE,
  PRISONER_BALANCE_RESET,
}
