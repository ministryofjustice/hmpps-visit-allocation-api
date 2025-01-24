package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient

@Service
class AllocationService(
  private val prisonerSearchClient: PrisonerSearchClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun startAllocation(prisonerId: String) {
    val prisoner = prisonerSearchClient.getPrisonerById(prisonerId)

    // TODO: Continue implementation - Get extra Vo / PVO for incentive level from incentives-api then store in DB.
  }
}
