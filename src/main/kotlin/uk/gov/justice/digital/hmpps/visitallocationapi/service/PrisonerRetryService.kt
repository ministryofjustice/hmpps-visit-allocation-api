package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.service.AllocationService.Companion.LOG
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationPrisonerRetrySqsService

@Service
class PrisonerRetryService(
  private val visitAllocationPrisonerRetrySqsService: VisitAllocationPrisonerRetrySqsService,
  private val incentivesClient: IncentivesClient,
  private val prisonerSearchClient: PrisonerSearchClient,
  @Lazy
  private val processPrisonerService: ProcessPrisonerService,
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

  fun handlePrisonerRetry(jobReference: String, prisonerId: String) {
    log.info("handle prisoner - $prisonerId on retry queue")
    val prisoner = prisonerSearchClient.getPrisonerById(prisonerId)
    val allIncentiveLevels = getIncentiveLevelsForPrison(prisonId = prisoner.prisonId)
    processPrisonerService.processPrisoner(prisonerId, jobReference, allIncentiveLevels, fromRetryQueue = true)
  }

  private fun getIncentiveLevelsForPrison(prisonId: String): List<PrisonIncentiveAmountsDto> {
    val incentiveLevelsForPrison = try {
      incentivesClient.getPrisonIncentiveLevels(prisonId)
    } catch (e: Exception) {
      val failureMessage = "failed to get incentive levels by prisonId - $prisonId in retry queue consumer"
      LOG.error(failureMessage, e)
      throw e
    }

    return incentiveLevelsForPrison
  }
}
