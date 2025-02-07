package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class VisitAllocationByPrisonJobListenerService(
  private val allocationService: AllocationService,
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun handleVisitAllocationJob(prisonCode: String) {
    log.info("received allocation job event: {}", prisonCode)
    allocationService.processPrison(prisonCode)
  }
}
