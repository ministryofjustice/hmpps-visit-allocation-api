package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistory
import java.time.LocalDateTime

@Repository
interface VisitOrderHistoryRepository : JpaRepository<VisitOrderHistory, Long> {
  @Query("SELECT voh FROM VisitOrderHistory voh WHERE voh.prisoner.prisonerId = :prisonerId and voh.createdTimestamp >= :fromDateTime order by voh.id asc")
  fun findVisitOrderHistoryByPrisonerIdAfterFromDateOrderByIdAsc(prisonerId: String, fromDateTime: LocalDateTime): List<VisitOrderHistory>

  @Query(
    value =
    """
    SELECT EXISTS (
      SELECT 1
      FROM visit_order_history voh
      JOIN visit_order_history_attributes attribute ON attribute.visit_order_history_id = voh.id
      WHERE voh.prisoner_id = :prisonerId
      AND voh.type = :visitOrderHistoryType
      AND attribute.attribute_type = :attributeType
      AND attribute.attribute_value = :visitReference
    )
    """,
    nativeQuery = true,
  )
  fun existsByPrisonerIdAndTypeAndVisitReferenceAttribute(
    prisonerId: String,
    visitOrderHistoryType: String,
    attributeType: String,
    visitReference: String,
  ): Boolean
}
