package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.projections.PrisonerBalance

@Repository
interface VisitOrderRepository : JpaRepository<VisitOrder, Long> {
  @Query(
    "SELECT vo.type AS type, COUNT(*) AS balance FROM visit_order vo " +
      "WHERE vo.prisoner_id = :prisonerId " +
      "AND vo.status IN ('AVAILABLE', 'ACCUMULATED') " +
      "GROUP BY vo.type",
    nativeQuery = true,
  )
  fun getPrisonerPositiveBalance(
    prisonerId: String,
  ): List<PrisonerBalance>

  @Query(
    "SELECT COUNT(vo) FROM VisitOrder vo WHERE vo.prisonerId = :prisonerId AND vo.type = :type AND vo.status = :status",
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
            ORDER BY created_timestamp ASC 
            LIMIT :amount)
    """,
    nativeQuery = true,
  )
  fun expireOldestAccumulatedVisitOrders(
    prisonerId: String,
    amount: Long,
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
              AND CAST(created_timestamp AS DATE) < CURRENT_DATE - INTERVAL '28 days'
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
              AND type = 'VO'
              AND status = 'AVAILABLE'
              AND CAST(created_timestamp AS DATE) < CURRENT_DATE - INTERVAL '28 days'
    """,
    nativeQuery = true,
  )
  fun updateAvailableVisitOrdersOver28DaysToAccumulated(
    prisonerId: String,
    type: VisitOrderType,
  ): Int

  @Transactional
  @Modifying
  @Query(
    value = """
      UPDATE visit_order
      SET status = 'EXPIRED', expiry_date = CURRENT_DATE
      WHERE id IN (
          SELECT id FROM visit_order
          WHERE prisoner_id = :prisonerId
            AND type = :#{#visitOrderType.name()}
            AND status in ('AVAILABLE', 'ACCUMULATED')
          ORDER BY created_timestamp ASC
          LIMIT :amountToExpire
      )
  """,
    nativeQuery = true,
  )
  fun expireVisitOrdersGivenAmount(
    prisonerId: String,
    visitOrderType: VisitOrderType,
    amountToExpire: Long?,
  ): Int

  @Transactional
  @Modifying
  fun deleteAllByPrisonerId(prisonerId: String)
}
