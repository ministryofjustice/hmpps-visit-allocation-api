package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

@Service
class PrisonerDetailsService(private val prisonerDetailsRepository: PrisonerDetailsRepository) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createPrisonerDetails(prisonerId: String, newLastAllocatedDate: LocalDate, newLastPvoAllocatedDate: LocalDate?): PrisonerDetails {
    LOG.info("PrisonerDetailsService - createPrisonerDetails called with prisonerId - $prisonerId, newLastAllocatedDate - $newLastAllocatedDate and newLastPvoAllocatedDate - $newLastPvoAllocatedDate")

    // Instead of using repository.save() we do a custom insert to avoid racing requests overwriting each other.
    // This insert forces an insert, and on duplicate insert it will fail and rollback txn for retry.
    prisonerDetailsRepository.insertNewPrisonerDetails(prisonerId = prisonerId, lastVoAllocatedVoDate = newLastAllocatedDate, lastPvoAllocatedVoDate = newLastPvoAllocatedDate)

    return prisonerDetailsRepository.findByIdWithLock(prisonerId).get()
  }

  fun getPrisonerDetailsWithLock(prisonerId: String): PrisonerDetails? {
    LOG.info("PrisonerDetailsService - getPrisonerDetailsWithLock called with prisonerId - $prisonerId")
    return prisonerDetailsRepository.findByIdWithLock(prisonerId).getOrNull()
  }

  fun getPrisonerDetails(prisonerId: String): PrisonerDetails? {
    LOG.info("PrisonerDetailsService - getPrisonerDetails called with prisonerId - $prisonerId")
    return prisonerDetailsRepository.findById(prisonerId).getOrNull()
  }

  fun removePrisonerDetails(prisonerId: String) {
    LOG.info("PrisonerDetailsService - removePrisonerDetails called with prisonerId - $prisonerId")
    val prisoner = prisonerDetailsRepository.findById(prisonerId)
    if (prisoner.isPresent) {
      prisonerDetailsRepository.delete(prisoner.get())
    }
  }
}
