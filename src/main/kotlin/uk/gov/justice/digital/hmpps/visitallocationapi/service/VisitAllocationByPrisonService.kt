package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderPrisonRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJobSqsService

@Service
class VisitAllocationByPrisonService(
  private val visitOrderPrisonRepository: VisitOrderPrisonRepository,
  private val visitAllocationEventJobSqsService: VisitAllocationEventJobSqsService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun triggerAllocationByPrison() {
    val activePrisons = visitOrderPrisonRepository.findByActive(true)
    log.info("Total active prisons for visit allocation job = ${activePrisons.size}")

    activePrisons.forEach {
      sendSqsMessageForPrison(it.prisonCode)
    }
  }

  private fun sendSqsMessageForPrison(prisonCode: String) {
    log.info("Adding message to event job queue for prisonCode: $prisonCode")
    try {
      visitAllocationEventJobSqsService.sendVisitAllocationEventToAllocationJobQueue(prisonCode)
    } catch (e: RuntimeException) {
      log.error("Sending message to event job queue for prisonCode: $prisonCode failed with error message - ${e.message}")
    }
  }
}
