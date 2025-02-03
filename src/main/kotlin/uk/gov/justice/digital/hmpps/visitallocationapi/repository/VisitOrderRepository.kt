package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate

@Repository
interface VisitOrderRepository : JpaRepository<VisitOrder, Long> {
  @Query(
    "SELECT vo.createdDate FROM VisitOrder vo WHERE vo.prisonerId = :prisonerId AND vo.type = :type ORDER BY vo.createdDate DESC LIMIT 1",
  )
  fun findLastAllocatedDate(
    prisonerId: String,
    type: VisitOrderType,
  ): LocalDate?
}
