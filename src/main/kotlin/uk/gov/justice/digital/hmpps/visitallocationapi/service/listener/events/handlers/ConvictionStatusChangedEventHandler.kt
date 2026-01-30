package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.additionalinfo.PrisonerConvictionStatusChangedInfo

@Service
class ConvictionStatusChangedEventHandler(
  objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val prisonerSearchClient: PrisonerSearchClient,
) : DomainEventHandler {

  private val processor = StandardDomainEventHandler(
    objectMapper = objectMapper,
    clazz = PrisonerConvictionStatusChangedInfo::class.java,
    shouldProcess = ::shouldProcess,
    isDpsPrison = ::isDpsPrison,
    processDps = { /* no-op */ },
    processNomis = { /* no-op */ },
  )

  override fun handle(domainEvent: DomainEvent) {
    processor.handle(domainEvent)
  }

  private fun shouldProcess(info: PrisonerConvictionStatusChangedInfo): Boolean = false


  private fun isDpsPrison(info: PrisonerConvictionStatusChangedInfo): Boolean {
    val prisoner = prisonerSearchClient.getPrisonerById(info.prisonerId)
    return prisonService.getPrisonEnabledForDpsByCode(prisoner.prisonId)
  }
}
