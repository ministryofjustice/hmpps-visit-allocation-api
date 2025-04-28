package uk.gov.justice.digital.hmpps.visitallocationapi.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationPrisonerRetrySqsService
import java.time.LocalDate

@Transactional
@Service
class PrisonerRetryService(
  private val visitAllocationPrisonerRetrySqsService: VisitAllocationPrisonerRetrySqsService,
  @Lazy
  private val allocationService: AllocationService,
  @Lazy
  private val prisonerDetailsService: PrisonerDetailsService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun sendMessageToPrisonerRetryQueue(jobReference: String, prisonerId: String) {
    try {
      log.info("Putting prisoner $prisonerId on the retry queue, jobReference - $jobReference")
      visitAllocationPrisonerRetrySqsService.sendToVisitAllocationPrisonerRetryQueue(allocationJobReference = jobReference, prisonerId = prisonerId)
    } catch (e: RuntimeException) {
      // ignore if a message could not be sent
      log.error("Failed to put prisoner $prisonerId on the retry queue, jobReference - $jobReference")
    }
  }

  suspend fun handlePrisonerRetry(prisonerId: String) {
    log.info("handle prisoner - $prisonerId on retry queue")

    val dpsPrisonerDetails: PrisonerDetails = withContext(Dispatchers.IO) {
      prisonerDetailsService.getPrisonerDetails(prisonerId)
        ?: prisonerDetailsService.createPrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    }

    allocationService.processPrisonerAllocation(dpsPrisonerDetails)

    withContext(Dispatchers.IO) {
      prisonerDetailsService.updatePrisonerDetails(dpsPrisonerDetails)
    }
  }
}
