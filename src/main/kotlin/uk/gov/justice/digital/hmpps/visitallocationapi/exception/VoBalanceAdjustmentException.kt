package uk.gov.justice.digital.hmpps.visitallocationapi.exception

import java.util.function.Supplier

class VoBalanceAdjustmentException(val messages: Array<String>) :
  RuntimeException(messages.joinToString()),
  Supplier<VoBalanceAdjustmentException> {
  override fun get(): VoBalanceAdjustmentException {
    val messages = message?.split(",")?.toTypedArray() ?: arrayOf()
    return VoBalanceAdjustmentException(messages)
  }
}
