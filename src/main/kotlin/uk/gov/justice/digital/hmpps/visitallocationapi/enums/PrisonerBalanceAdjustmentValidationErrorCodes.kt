package uk.gov.justice.digital.hmpps.visitallocationapi.enums

enum class PrisonerBalanceAdjustmentValidationErrorCodes(val description: String) {
  VO_OR_PVO_NOT_SUPPLIED("Either voAmount or pvoAmount must be provided"),
  VO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX("VO count after adjustment will take it past max allowed"),
  VO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO("VO count after adjustment will take it below zero"),
  PVO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX("PVO count after adjustment will take it past max allowed"),
  PVO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO("PVO count after adjustment will take it below zero"),
}
