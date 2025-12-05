package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistory
import java.time.LocalDate

@Repository
interface VisitOrderHistoryRepository : JpaRepository<VisitOrderHistory, Long> {
  @Query("SELECT voh FROM VisitOrderHistory voh WHERE voh.prisoner.prisonerId = :prisonerId and voh.createdTimestamp >= :fromDate order by voh.id asc")
  fun findVisitOrderHistoryByPrisonerIdAfterFromDateOrderByIdAsc(prisonerId: String, fromDate: LocalDate): List<VisitOrderHistory>
}
