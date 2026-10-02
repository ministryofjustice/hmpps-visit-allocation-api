package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.jobs.VisitAllocationEventJobDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJobSqsService

@Service
class VisitAllocationJobTriggerService(
  private val prisonService: PrisonService,
  private val visitOrderAllocationJobService: VisitOrderAllocationJobService,
  private val visitAllocationEventJobSqsService: VisitAllocationEventJobSqsService,
  private val telemetryClientService: TelemetryClientService,
) {
  companion object {
    private val LOG = LoggerFactory.getLogger(this::class.java)
  }

  fun triggerVisitAllocationForActivePrisons(): VisitAllocationEventJobDto {
    LOG.info("Trigger allocation by prison started")
    val activePrisons = prisonService.getActivePrisonsForAllocation()
    LOG.info("Total active prisons for visit allocation job = ${activePrisons.size}")

    val allocationJobReference = visitOrderAllocationJobService.createAllocationJob(activePrisons.map { it.agencyId })
    activePrisons.forEach { sendSqsMessageForPrison(allocationJobReference, it.agencyId) }

    return VisitAllocationEventJobDto(allocationJobReference, totalActivePrisons = activePrisons.size)
  }

  private fun sendSqsMessageForPrison(allocationJobReference: String, prisonCode: String) {
    LOG.info("Adding message to event job queue for prisonCode: $prisonCode")

    try {
      visitAllocationEventJobSqsService.sendVisitAllocationEventToAllocationJobQueue(allocationJobReference, prisonCode)
    } catch (e: RuntimeException) {
      LOG.error("Sending message to event job queue for prisonCode: $prisonCode failed with error message - ${e.message}")
      telemetryClientService.trackEvent(
        TelemetryEventType.PRISON_ALLOCATION_QUEUE_PUBLISH_FAILED,
        mapOf(
          "allocationJobReference" to allocationJobReference,
          "prisonCode" to prisonCode,
          "exceptionType" to e.javaClass.simpleName,
          "failureMessage" to e.message.orEmpty().take(256),
        ),
      )
    }
  }
}
