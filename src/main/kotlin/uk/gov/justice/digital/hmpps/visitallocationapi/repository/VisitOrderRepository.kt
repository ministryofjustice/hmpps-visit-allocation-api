package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
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

  @Query(
    "SELECT COUNT (vo) FROM VisitOrder vo WHERE vo.prisonerId = :prisonerId AND vo.type = :type AND vo.status = :status",
  )
  fun countAllVisitOrders(
    prisonerId: String,
    type: VisitOrderType,
    status: VisitOrderStatus,
  ): Int

  @Transactional
  @Modifying
  @Query(
    value = """
        UPDATE visit_order 
        SET status = 'EXPIRED', expiry_date = CURRENT_DATE
        WHERE id IN (SELECT id 
            FROM visit_order 
            WHERE prisoner_id = :prisonerId 
              AND type = 'VO'
              AND status = 'ACCUMULATED' 
            ORDER BY created_date ASC 
            LIMIT :amount)
    """,
    nativeQuery = true,
  )
  fun expireOldestAccumulatedVisitOrders(
    prisonerId: String,
    amount: Int,
  ): Int

  @Transactional
  @Modifying
  @Query(
    value = """
        UPDATE visit_order
        SET status = 'EXPIRED', expiry_date = CURRENT_DATE
            WHERE prisoner_id = :prisonerId
              AND type = 'PVO'
              AND status = 'AVAILABLE'
              AND created_date < CURRENT_DATE - INTERVAL '28 days'
    """,
    nativeQuery = true,
  )
  fun expirePrivilegedVisitOrdersOver28Days(
    prisonerId: String,
  ): Int

  @Transactional
  @Modifying
  @Query(
    value = """
        UPDATE visit_order
        SET status = 'ACCUMULATED'
            WHERE prisoner_id = :prisonerId
              AND type = :#{#type.name()}
              AND status = 'AVAILABLE'
              AND created_date < CURRENT_DATE - INTERVAL '28 days'
    """,
    nativeQuery = true,
  )
  fun updateAvailableVisitOrdersOver28DaysToAccumulated(
    prisonerId: String,
    type: VisitOrderType,
  )
}
