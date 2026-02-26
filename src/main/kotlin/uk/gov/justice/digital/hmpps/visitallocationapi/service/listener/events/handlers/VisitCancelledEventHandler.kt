package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.VisitSchedulerClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ProcessPrisonerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.SnsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.VisitCancelledInfo

@Service
class VisitCancelledEventHandler(
  @param:Qualifier("objectMapper")
  private val objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val visitSchedulerClient: VisitSchedulerClient,
  private val prisonerSearchClient: PrisonerSearchClient,
  private val processPrisonerService: ProcessPrisonerService,
  private val snsService: SnsService,
  private val changeLogService: ChangeLogService,
) : DomainEventHandler {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val CONVICTED = "Convicted"
  }

  override fun handle(domainEvent: DomainEvent) {
    LOG.info("VisitCancelledEventHandler called for event prison.visit-cancelled")
    val additionalInfo = objectMapper.readValue(domainEvent.additionalInformation, VisitCancelledInfo::class.java)

    LOG.info("Getting visit using reference - ${additionalInfo.reference}")
    val visit = visitSchedulerClient.getVisitByReference(additionalInfo.reference)
    val prisoner = getPrisonerOrHandleMergedPrisoner(visit.prisonerId)
    if (prisoner == null) {
      return
    }

    if (prisonService.getPrisonEnabledForDpsByCode(prisoner.prisonId)) {
      LOG.info("Prisoner ${prisoner.prisonerId} is in ${prisoner.prisonId} which is enabled for DPS, processing event")

      if (prisoner.convictedStatus == CONVICTED) {
        val changeLogReference = processPrisonerService.processPrisonerVisitOrderRefund(visit)

        if (changeLogReference != null) {
          val changeLog = changeLogService.findChangeLogForPrisonerByReference(visit.prisonerId, changeLogReference)
          if (changeLog != null) {
            snsService.sendPrisonAllocationAdjustmentCreatedEvent(changeLog)
          }
        }
      } else {
        LOG.info("Prisoner ${prisoner.prisonerId} is on Remand, no processing needed")
      }
    } else {
      LOG.info("Prison ${prisoner.prisonId} is not enabled for DPS, skipping processing")
    }
  }

  private fun getPrisonerOrHandleMergedPrisoner(prisonerId: String): PrisonerDto? {
    try {
      return prisonerSearchClient.getPrisonerById(prisonerId)
    } catch (e: WebClientResponseException) {
      if (e.statusCode.value() != 404) {
        throw e
      }

      LOG.info("Prisoner $prisonerId not found in prisoner search, checking for merged prisoner")

      val mergedPrisoner = prisonerSearchClient.findMergedPrisonerByIdentifierTypeMerged(prisonerId)
      if (!(mergedPrisoner.content.isNullOrEmpty())) {
        LOG.info("Merged prisoner found for $prisonerId, prisoner-search returned ${mergedPrisoner.content}, skipping processing event")
        return null
      } else {
        LOG.info("No merged prisoner found for $prisonerId, reached illegal state - throwing exception")
        throw IllegalStateException("No merged prisoner found for $prisonerId")
      }
    }
  }
}
