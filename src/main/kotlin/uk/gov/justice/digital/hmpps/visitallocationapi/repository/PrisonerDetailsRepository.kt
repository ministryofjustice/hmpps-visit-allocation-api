package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import java.util.*

@Repository
interface PrisonerDetailsRepository : JpaRepository<PrisonerDetails, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT pd FROM PrisonerDetails pd WHERE pd.prisonerId = :id")
  fun findByIdForUpdate(id: String): Optional<PrisonerDetails>
}
