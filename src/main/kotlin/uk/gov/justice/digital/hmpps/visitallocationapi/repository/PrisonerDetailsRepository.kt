package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import java.time.LocalDate

@Repository
interface PrisonerDetailsRepository : JpaRepository<PrisonerDetails, Long> {
  fun findByPrisonerId(prisonerId: String): PrisonerDetails?

  @Transactional
  @Modifying
  fun deleteByPrisonerId(prisonerId: String)

  @Transactional
  @Modifying
  @Query("UPDATE PrisonerDetails pd SET pd.lastVoAllocatedDate = :newLastAllocatedDate WHERE pd.prisonerId = :prisonerId")
  fun updatePrisonerLastVoAllocatedDate(prisonerId: String, newLastAllocatedDate: LocalDate)

  @Transactional
  @Modifying
  @Query("UPDATE PrisonerDetails pd SET pd.lastPvoAllocatedDate = :newLastAllocatedDate WHERE pd.prisonerId = :prisonerId")
  fun updatePrisonerLastPvoAllocatedDate(prisonerId: String, newLastAllocatedDate: LocalDate)
}
