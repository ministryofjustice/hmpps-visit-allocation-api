package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationPrisonerRetrySqsService

@Service
class PrisonerRetryService(
  private val visitAllocationPrisonerRetrySqsService: VisitAllocationPrisonerRetrySqsService,
  private val incentivesClient: IncentivesClient,
  private val prisonerSearchClient: PrisonerSearchClient,
  @param:Lazy
  private val processPrisonerService: ProcessPrisonerService,
  private val snsService: SnsService,
  private val changeLogService: ChangeLogService,
) {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun sendMessageToPrisonerRetryQueue(jobReference: String, prisonerId: String) {
    try {
      logger.info("Putting prisoner $prisonerId on the retry queue, jobReference - $jobReference")
      visitAllocationPrisonerRetrySqsService.sendToVisitAllocationPrisonerRetryQueue(allocationJobReference = jobReference, prisonerId = prisonerId)
    } catch (e: RuntimeException) {
      // ignore if a message could not be sent
      logger.error("Failed to put prisoner $prisonerId on the retry queue, jobReference - $jobReference")
    }
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
  fun handlePrisonerRetry(jobReference: String, prisonerId: String) {
    logger.info("handle prisoner - $prisonerId on retry queue")
    val prisoner = prisonerSearchClient.getPrisonerById(prisonerId)
    val allIncentiveLevels = getIncentiveLevelsForPrison(prisonId = prisoner.prisonId)
    val changeLogReference = processPrisonerService.processPrisonerAllocation(prisonerId, jobReference, allIncentiveLevels, fromRetryQueue = true)
    if (changeLogReference != null) {
      val changeLog = changeLogService.findChangeLogForPrisonerByReference(prisonerId, changeLogReference)
      if (changeLog != null) {
        snsService.sendPrisonAllocationAdjustmentCreatedEvent(changeLog)
      }
    }
  }

  private fun getIncentiveLevelsForPrison(prisonId: String): List<PrisonIncentiveAmountsDto> {
    val incentiveLevelsForPrison = try {
      incentivesClient.getPrisonIncentiveLevels(prisonId)
    } catch (e: Exception) {
      val failureMessage = "failed to get incentive levels by prisonId - $prisonId in retry queue consumer"
      logger.error(failureMessage, e)
      throw e
    }

    return incentiveLevelsForPrison
  }
}
