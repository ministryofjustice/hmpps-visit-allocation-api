package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.projections.PrisonerBalance

@Repository
interface NegativeVisitOrderRepository : JpaRepository<NegativeVisitOrder, Long> {
  @Query(
    "SELECT nvo.type AS type, COUNT(*) AS balance FROM negative_visit_order nvo " +
      "WHERE nvo.prisoner_id = :prisonerId " +
      "AND nvo.status = 'USED' " +
      "GROUP BY nvo.type",
    nativeQuery = true,
  )
  fun getPrisonerNegativeBalance(
    prisonerId: String,
  ): List<PrisonerBalance>

  @Query(
    value = """
    SELECT COUNT(*) 
    FROM negative_visit_order 
    WHERE prisoner_id = :prisonerId 
      AND type = :#{#type.name()}
      AND status = :#{#status.name()}
  """,
    nativeQuery = true,
  )
  fun countAllNegativeVisitOrders(
    prisonerId: String,
    type: VisitOrderType,
    status: NegativeVisitOrderStatus,
  ): Int

  @Transactional
  @Modifying
  @Query(
    value = """
        UPDATE negative_visit_order
        SET status = 'REPAID', repaid_date = CURRENT_DATE
        WHERE id IN (
            SELECT id 
            FROM negative_visit_order
            WHERE prisoner_id = :prisonerId
              AND type = :#{#visitOrderType.name()}
              AND status = 'USED'
            ORDER BY created_timestamp ASC
            LIMIT :amountToExpire
        )
    """,
    nativeQuery = true,
  )
  fun repayNegativeVisitOrdersGivenAmount(
    prisonerId: String,
    visitOrderType: VisitOrderType,
    amountToExpire: Long?,
  ): Int

  @Transactional
  @Modifying
  @Query(
    value = "DELETE FROM negative_visit_order WHERE prisoner_id = :prisonerId",
    nativeQuery = true,
  )
  fun deleteAllByPrisonerId(prisonerId: String)
}
