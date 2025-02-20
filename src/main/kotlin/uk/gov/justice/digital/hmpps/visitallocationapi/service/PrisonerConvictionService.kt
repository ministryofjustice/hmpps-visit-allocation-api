package uk.gov.justice.digital.hmpps.visitallocationapi.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderPrisonRepository

@Service
class PrisonerConvictionService(
  private val visitOrderPrisonRepository: VisitOrderPrisonRepository,
  private val prisonerSearchClient: PrisonerSearchClient,
  private val allocationService: AllocationService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun processPrisonerConvictionStatusChange(prisonerId: String) {
    log.info("Entered PrisonerConvictionService processPrisonerConvictionStatusChange with prisonerId - $prisonerId")
    val prisoner = prisonerSearchClient.getPrisonerById(prisonerId)

    if (withContext(Dispatchers.IO) { visitOrderPrisonRepository.findByPrisonCode(prisoner.prisonId) }?.active == true) {
      allocationService.processPrisonerAllocation(prisonerId, prisonerDto = prisoner)
    }
  }
}
