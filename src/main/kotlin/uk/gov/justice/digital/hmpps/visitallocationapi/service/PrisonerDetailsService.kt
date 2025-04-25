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

  fun updateVoLastCreatedDate(prisonerId: String, newLastAllocatedDate: LocalDate) {
    LOG.info("Entered PrisonerDetailsService updateVoLastCreatedDate for prisoner $prisonerId with date $newLastAllocatedDate")

    prisonerDetailsRepository.updatePrisonerLastVoAllocatedDate(prisonerId, newLastAllocatedDate)
  }

  fun createNewPrisonerDetails(prisonerId: String, newLastAllocatedDate: LocalDate, newLastPvoAllocatedDate: LocalDate?): PrisonerDetails {
    LOG.info("Prisoner $prisonerId not found, creating new record")
    val newPrisoner = PrisonerDetails(
      prisonerId = prisonerId,
      lastVoAllocatedDate = newLastAllocatedDate,
      lastPvoAllocatedDate = newLastPvoAllocatedDate,
    )
    return prisonerDetailsRepository.save(newPrisoner)
  }

  fun removePrisonerDetails(prisonerId: String) {
    prisonerDetailsRepository.deleteByPrisonerId(prisonerId)
  }

  fun updatePvoLastCreatedDate(prisonerId: String, newLastAllocatedDate: LocalDate) {
    LOG.info("Entered PrisonerDetailsService updatePvoLastCreatedDate for prisoner $prisonerId with date $newLastAllocatedDate")
    prisonerDetailsRepository.updatePrisonerLastPvoAllocatedDate(prisonerId, newLastAllocatedDate)
  }
}
