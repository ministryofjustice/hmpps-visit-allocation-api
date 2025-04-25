package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog

@Repository
interface ChangeLogRepository : JpaRepository<ChangeLog, Long> {
  @Transactional
  @Modifying
  @Query(
    value = "DELETE FROM change_log WHERE prisoner_id = :prisonerId",
    nativeQuery = true,
  )
  fun deleteAllByPrisonerId(prisonerId: String)
}
