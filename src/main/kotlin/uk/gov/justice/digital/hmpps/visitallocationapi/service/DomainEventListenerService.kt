package uk.gov.justice.digital.hmpps.visitallocationapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ConvictedStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerConvictionStatusChangedInfo

@Service
class DomainEventListenerService(
  val objectMapper: ObjectMapper,
  val prisonerConvictionService: PrisonerConvictionService,
) {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val CONVICTION_STATUS_UPDATED_EVENT_TYPE = "prisoner-offender-search.prisoner.convicted-status-changed"
  }

  suspend fun handleMessage(domainEvent: DomainEvent) {
    LOG.info("received event: {}", domainEvent)

    when (domainEvent.eventType) {
      CONVICTION_STATUS_UPDATED_EVENT_TYPE -> processConvictionStatusChangedEvent(domainEvent)
      else -> LOG.info("invalid message type: {}", domainEvent)
    }
  }

  private fun processConvictionStatusChangedEvent(domainEvent: DomainEvent) {
    LOG.info("received conviction status changed event: {}", domainEvent)
    val additionalInfo = getAdditionalInfo(domainEvent)
    if (additionalInfo.convictedStatus == ConvictedStatus.CONVICTED.value) {
      runBlocking {
        prisonerConvictionService.processPrisonerConvictionStatusChange(additionalInfo.prisonerId)
      }
    }
  }

  private fun getAdditionalInfo(domainEvent: DomainEvent): PrisonerConvictionStatusChangedInfo = objectMapper.readValue(domainEvent.additionalInformation, PrisonerConvictionStatusChangedInfo::class.java)
}
