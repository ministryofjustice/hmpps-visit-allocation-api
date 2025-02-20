package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJob

@Service
class VisitAllocationByPrisonJobListenerService(
  private val allocationService: AllocationService,
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun handleVisitAllocationJob(visitAllocationEventJob: VisitAllocationEventJob) {
    with(visitAllocationEventJob) {
      log.info("received allocation job event with reference {} and prison code: {}", jobReference, prisonCode.uppercase())
      allocationService.processPrison(jobReference, prisonCode.uppercase())
    }
  }
}
