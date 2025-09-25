package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.jobs.VisitAllocationEventJobDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.SpecialPrisonCodes
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationJob
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationPrisonJob
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationPrisonJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJobSqsService
import java.time.LocalDateTime

@Transactional
@Service
class PrisonService(
  private val prisonApiClient: PrisonApiClient,
  private val visitOrderAllocationJobRepository: VisitOrderAllocationJobRepository,
  private val visitOrderAllocationPrisonJobRepository: VisitOrderAllocationPrisonJobRepository,
  private val visitAllocationEventJobSqsService: VisitAllocationEventJobSqsService,
  @Value("\${feature.dps-process-special-prison-codes.enabled}") val dpsProcessSpecialPrisonCodes: Boolean,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getPrisonEnabledForDpsByCode(prisonCode: String): Boolean {
    if (dpsProcessSpecialPrisonCodes && SpecialPrisonCodes.entries.any { it.name == prisonCode }) {
      LOG.info("Special prison code $prisonCode found, feature is enabled, returning true for is enabled for DPS")
      return true
    } else {
      return prisonApiClient.getPrisonEnabledForDps(prisonCode)
    }
  }

  fun triggerVisitAllocationForActivePrisons(): VisitAllocationEventJobDto {
    log.info("Trigger allocation by prison started")
    val activePrisons = prisonApiClient.getAllServicePrisonsEnabledForDps()
    val allocationJobReference = auditOrderAllocationJob(totalActivePrisons = activePrisons.size).reference
    log.info("Total active prisons for visit allocation job = ${activePrisons.size}")

    activePrisons.forEach {
      val prisonCode = it.agencyId
      auditOrderAllocationPrisonJob(allocationJobReference, prisonCode)
      sendSqsMessageForPrison(allocationJobReference, prisonCode)
    }

    return VisitAllocationEventJobDto(allocationJobReference, totalActivePrisons = activePrisons.size)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun setVisitOrderAllocationPrisonJobStartTime(jobReference: String, prisonCode: String) {
    visitOrderAllocationPrisonJobRepository.updateStartTimestamp(jobReference, prisonCode, LocalDateTime.now())
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun setVisitOrderAllocationPrisonJobEndTimeAndFailureMessage(jobReference: String, prisonCode: String, failureMessage: String) {
    visitOrderAllocationPrisonJobRepository.updateFailureMessageAndEndTimestamp(allocationJobReference = jobReference, prisonCode = prisonCode, failureMessage, LocalDateTime.now())
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun setVisitOrderAllocationPrisonJobEndTimeAndStats(jobReference: String, prisonCode: String, totalConvictedPrisoners: Int, totalPrisonersProcessed: Int, totalPrisonersFailedOrSkipped: Int) {
    visitOrderAllocationPrisonJobRepository.updateEndTimestampAndStats(allocationJobReference = jobReference, prisonCode = prisonCode, LocalDateTime.now(), totalPrisoners = totalConvictedPrisoners, processedPrisoners = totalPrisonersProcessed, failedOrSkippedPrisoners = totalPrisonersFailedOrSkipped)
  }

  private fun auditOrderAllocationJob(totalActivePrisons: Int): VisitOrderAllocationJob {
    val visitOrderAllocationJob = VisitOrderAllocationJob(totalPrisons = totalActivePrisons)
    return visitOrderAllocationJobRepository.save(visitOrderAllocationJob)
  }

  private fun auditOrderAllocationPrisonJob(allocationJobReference: String, prisonCode: String) {
    val visitOrderAllocationPrisonJob = VisitOrderAllocationPrisonJob(allocationJobReference, prisonCode)
    visitOrderAllocationPrisonJobRepository.save(visitOrderAllocationPrisonJob)
  }

  private fun sendSqsMessageForPrison(allocationJobReference: String, prisonCode: String) {
    log.info("Adding message to event job queue for prisonCode: $prisonCode")

    try {
      visitAllocationEventJobSqsService.sendVisitAllocationEventToAllocationJobQueue(allocationJobReference, prisonCode)
    } catch (e: RuntimeException) {
      log.error("Sending message to event job queue for prisonCode: $prisonCode failed with error message - ${e.message}")
    }
  }
}
