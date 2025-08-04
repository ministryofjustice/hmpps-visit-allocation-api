package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationPrisonerRetrySqsService

@Service
class PrisonerRetryService(
  private val visitAllocationPrisonerRetrySqsService: VisitAllocationPrisonerRetrySqsService,
  private val incentivesClient: IncentivesClient,
  private val prisonerSearchClient: PrisonerSearchClient,
  @Lazy
  private val processPrisonerService: ProcessPrisonerService,
  private val snsService: SnsService,
  private val changeLogService: ChangeLogService,
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
    val allIncentiveLevels = incentivesClient.getPrisonIncentiveLevels(prisonId = prisoner.prisonId)
    val changeLogReference = processPrisonerService.processPrisonerAllocation(prisonerId, jobReference, allIncentiveLevels, fromRetryQueue = true)
    if (changeLogReference != null) {
      val changeLog = changeLogService.findChangeLogForPrisonerByReference(prisonerId, changeLogReference)
      if (changeLog != null) {
        snsService.sendPrisonAllocationAdjustmentCreatedEvent(changeLog)
      }
    }
  }
}
