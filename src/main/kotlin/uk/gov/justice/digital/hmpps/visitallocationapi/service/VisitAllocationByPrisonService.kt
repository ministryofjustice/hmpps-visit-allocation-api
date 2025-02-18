package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.jobs.VisitAllocationEventJobDto
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationJob
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationPrisonJob
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationPrisonJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderPrisonRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJobSqsService

@Service
class VisitAllocationByPrisonService(
  private val visitOrderPrisonRepository: VisitOrderPrisonRepository,
  private val visitOrderAllocationJobRepository: VisitOrderAllocationJobRepository,
  private val visitOrderAllocationPrisonJobRepository: VisitOrderAllocationPrisonJobRepository,
  private val visitAllocationEventJobSqsService: VisitAllocationEventJobSqsService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun triggerVisitAllocationForActivePrisons(): VisitAllocationEventJobDto {
    log.info("Trigger allocation by prison started")
    val activePrisons = visitOrderPrisonRepository.findByActive(true)
    val allocationJobReference = auditOrderAllocationJob(totalActivePrisons = activePrisons.size).reference
    log.info("Total active prisons for visit allocation job = ${activePrisons.size}")

    activePrisons.forEach {
      val prisonCode = it.prisonCode
      auditOrderAllocationPrisonJob(allocationJobReference, prisonCode)
      sendSqsMessageForPrison(allocationJobReference, prisonCode)
    }

    return VisitAllocationEventJobDto(allocationJobReference, totalActivePrisons = activePrisons.size)
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
