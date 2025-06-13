package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import java.util.*

@Repository
interface ChangeLogRepository : JpaRepository<ChangeLog, Long> {
  fun findAllByPrisonerId(prisonerId: String): List<ChangeLog>?

  fun findFirstByPrisonerIdAndReference(prisonerId: String, reference: UUID): ChangeLog?
}
