package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.ServicePrisonDto

@Service
class PrisonService(
  private val prisonApiClient: PrisonApiClient,
) {
  companion object {
    const val ALL_PRISON_CODE = "*ALL*"
  }

  fun getPrisonEnabledForDpsByCode(prisonCode: String): Boolean = prisonApiClient.getPrisonEnabledForDps(prisonCode)

  fun getActivePrisonsForAllocation(): List<ServicePrisonDto> {
    val prisonsEnabledForDps = prisonApiClient.getAllServicePrisonsEnabledForDps()
    return if (prisonsEnabledForDps.any { it.agencyId == ALL_PRISON_CODE }) {
      prisonApiClient.getAllActivePrisons()
    } else {
      prisonsEnabledForDps
    }
  }
}
