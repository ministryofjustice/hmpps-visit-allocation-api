package uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent

abstract class BaseDomainEventHandler<T : Any>(
  protected val objectMapper: ObjectMapper,
  protected val prisonService: PrisonService,
  protected val prisonerSearchClient: PrisonerSearchClient,
  private val clazz: Class<T>,
) {

  /**
   * Provide a default (concrete) implementation of 'handle', as only some sub-class handlers will need to diverge from the
   * standard approach taken in this method.
   */
  open fun handle(domainEvent: DomainEvent) {
    val additionalInfo = objectMapper.readValue(domainEvent.additionalInformation, clazz)

    if (shouldProcess(additionalInfo)) {
      if (isDpsPrison(additionalInfo)) {
        processDps(additionalInfo)
      } else {
        processNomis(additionalInfo)
      }
    }
  }

  protected abstract fun shouldProcess(additionalInfo: T): Boolean
  protected abstract fun isDpsPrison(additionalInfo: T): Boolean
  protected abstract fun processDps(additionalInfo: T)
  protected abstract fun processNomis(additionalInfo: T)
}
