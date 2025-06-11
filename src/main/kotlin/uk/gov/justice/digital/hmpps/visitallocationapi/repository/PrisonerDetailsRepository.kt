package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import java.util.*

@Repository
interface PrisonerDetailsRepository : JpaRepository<PrisonerDetails, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")])
  @Query("SELECT pd FROM PrisonerDetails pd WHERE pd.prisonerId = :id")
  fun findByIdWithLock(id: String): Optional<PrisonerDetails>

  @Query("SELECT pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
  fun acquireAdvisoryLockForInsert(@Param("lockKey") lockKey: Long)
}
