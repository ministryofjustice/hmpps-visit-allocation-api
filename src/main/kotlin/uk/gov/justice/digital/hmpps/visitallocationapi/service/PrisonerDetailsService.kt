package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

@Transactional
@Service
class PrisonerDetailsService(private val prisonerDetailsRepository: PrisonerDetailsRepository) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createPrisonerDetails(prisonerId: String, newLastAllocatedDate: LocalDate, newLastPvoAllocatedDate: LocalDate?): PrisonerDetails {
    LOG.info("PrisonerDetailsService - createPrisonerDetails called with prisonerId - $prisonerId and newLastAllocatedDate - $newLastPvoAllocatedDate")
    return prisonerDetailsRepository.saveAndFlush(
      PrisonerDetails(
        prisonerId = prisonerId,
        lastVoAllocatedDate = newLastAllocatedDate,
        lastPvoAllocatedDate = newLastPvoAllocatedDate,
      ),
    )
  }

  fun getPrisonerDetails(prisonerId: String): PrisonerDetails? {
    LOG.info("PrisonerDetailsService - getPrisonerDetails called with prisonerId - $prisonerId")
    return prisonerDetailsRepository.findById(prisonerId).getOrNull()
  }

  fun updatePrisonerDetails(prisoner: PrisonerDetails): PrisonerDetails {
    LOG.info("PrisonerDetailsService - updatePrisonerDetails called with new prisoner details - $prisoner")
    return prisonerDetailsRepository.saveAndFlush(prisoner)
  }

  fun removePrisonerDetails(prisonerId: String) {
    LOG.info("PrisonerDetailsService - removePrisonerDetails called with prisonerId - $prisonerId")
    val prisoner = prisonerDetailsRepository.findById(prisonerId)
    if (prisoner.isPresent) {
      prisonerDetailsRepository.delete(prisoner.get())
    }
  }
}
