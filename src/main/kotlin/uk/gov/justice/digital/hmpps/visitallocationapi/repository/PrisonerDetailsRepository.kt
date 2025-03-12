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
  @Query(
    value = "UPDATE prisoner_details SET last_vo_allocated_date = :newLastAllocatedDate WHERE prisoner_id = :prisonerId",
    nativeQuery = true,
  )
  fun updatePrisonerLastVoAllocatedDate(prisonerId: String, newLastAllocatedDate: LocalDate)

  @Transactional
  @Modifying
  @Query(
    value = "UPDATE prisoner_details SET last_pvo_allocated_date = :newLastAllocatedDate WHERE prisoner_id = :prisonerId",
    nativeQuery = true,
  )
  fun updatePrisonerLastPvoAllocatedDate(prisonerId: String, newLastAllocatedDate: LocalDate)
}
