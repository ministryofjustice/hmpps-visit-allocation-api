package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.processors

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ConvictedStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.service.AllocationService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerConvictionStatusChangedInfo

@Component
class PrisonerConvictionStatusUpdatedProcessor(
  val allocationService: AllocationService,
  val objectMapper: ObjectMapper,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processEvent(domainEvent: DomainEvent) {
    LOG.info("received conviction status changed event: {}", domainEvent)
    val additionalInfo = getAdditionalInfo(domainEvent)
    if (additionalInfo.convictedStatus == ConvictedStatus.CONVICTED.value) { // TODO: Confirm how the string will come through
      allocationService.startAllocation(additionalInfo.prisonerId)
    }
  }

  private fun getAdditionalInfo(domainEvent: DomainEvent): PrisonerConvictionStatusChangedInfo {
    return objectMapper.readValue(domainEvent.additionalInformation, PrisonerConvictionStatusChangedInfo::class.java)
  }
}
