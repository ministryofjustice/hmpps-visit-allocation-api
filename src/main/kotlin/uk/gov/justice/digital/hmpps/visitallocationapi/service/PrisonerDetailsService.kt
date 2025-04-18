package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import java.time.LocalDate

@Transactional
@Service
class PrisonerDetailsService(private val prisonerDetailsRepository: PrisonerDetailsRepository) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getPrisoner(prisonerId: String): PrisonerDetails? = prisonerDetailsRepository.findByPrisonerId(prisonerId)

  fun updateVoLastCreatedDateOrCreatePrisoner(prisonerId: String, newLastAllocatedDate: LocalDate) {
    LOG.info("Entered PrisonerDetailsService updateVoLastCreatedDateOrCreatePrisoner for prisoner $prisonerId with date $newLastAllocatedDate")
    // Check if the prisoner exists
    val prisonerDetails = prisonerDetailsRepository.findByPrisonerId(prisonerId)

    if (prisonerDetails != null) {
      LOG.info("Existing prisoner $prisonerId found - Updating last VO allocated date to $newLastAllocatedDate")
      // If prisoner exists, update the record
      prisonerDetailsRepository.updatePrisonerLastVoAllocatedDate(prisonerId, newLastAllocatedDate)
    } else {
      // If prisoner does not exist, create a new record
      createNewPrisonerDetails(prisonerId, newLastAllocatedDate, null)
    }
  }

  fun createNewPrisonerDetails(prisonerId: String, newLastAllocatedDate: LocalDate, newLastPvoAllocatedDate: LocalDate?) {
    LOG.info("Prisoner $prisonerId not found, creating new record")
    val newPrisoner = PrisonerDetails(
      prisonerId = prisonerId,
      lastVoAllocatedDate = newLastAllocatedDate,
      lastPvoAllocatedDate = newLastPvoAllocatedDate,
    )
    prisonerDetailsRepository.save(newPrisoner)
  }

  fun removePrisonerDetails(prisonerId: String) {
    prisonerDetailsRepository.deleteByPrisonerId(prisonerId)
  }

  fun updatePvoLastCreatedDate(prisonerId: String, newLastAllocatedDate: LocalDate) {
    LOG.info("Entered PrisonerDetailsService updatePvoLastCreatedDate for prisoner $prisonerId with date $newLastAllocatedDate")
    prisonerDetailsRepository.updatePrisonerLastPvoAllocatedDate(prisonerId, newLastAllocatedDate)
  }
}
