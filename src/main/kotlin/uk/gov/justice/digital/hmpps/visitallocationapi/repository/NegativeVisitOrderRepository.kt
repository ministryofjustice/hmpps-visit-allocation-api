package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.projections.NegativePrisonerBalance

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
  ): List<NegativePrisonerBalance>

  @Transactional
  @Modifying
  @Query(
    value = """
        UPDATE negative_visit_order
        SET status = 'REPAID', repaid_date = CURRENT_DATE
            WHERE prisoner_id = :prisonerId
              AND type = :negativeVisitOrderType
              AND status = 'USED'
            LIMIT :amountToExpire
    """,
    nativeQuery = true,
  )
  fun repayVisitOrdersGivenAmount(
    prisonerId: String,
    negativeVisitOrderType: NegativeVisitOrderType,
    amountToExpire: Int,
  ): Int

  @Transactional
  @Modifying
  @Query(
    value = """
        UPDATE negative_visit_order
        SET status = 'REPAID', repaid_date = CURRENT_DATE
            WHERE prisoner_id = :prisonerId
              AND type = :negativeVisitOrderType
              AND status = 'USED'
    """,
    nativeQuery = true,
  )
  fun repayAllVisitOrders(
    prisonerId: String,
    negativeVisitOrderType: NegativeVisitOrderType,
  ): Int
}
