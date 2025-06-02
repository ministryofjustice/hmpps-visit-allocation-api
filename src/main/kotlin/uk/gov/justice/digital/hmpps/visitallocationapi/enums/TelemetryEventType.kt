package uk.gov.justice.digital.hmpps.visitallocationapi.enums

enum class TelemetryEventType(val telemetryEventName: String) {
  BALANCES_OUT_OF_SYNC("balances-out-of-sync"),
  VO_CONSUMED_BY_VISIT("allocation-api-vo-consumed-by-visit"),
  VO_REFUNDED_AFTER_VISIT_CANCELLATION("allocation-api-vo-refunded-by-visit-cancelled"),
  VO_ADDED_POST_MERGE("allocation-api-vo-added-post-merge"),
  VO_PRISONER_BALANCE_RESET("allocation-api-vo-prisoner-balance-reset"),
}
