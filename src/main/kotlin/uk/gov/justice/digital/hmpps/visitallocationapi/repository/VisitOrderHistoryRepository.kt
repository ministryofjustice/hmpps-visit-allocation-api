package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryAttributeType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistory
import java.time.LocalDateTime

@Repository
interface VisitOrderHistoryRepository : JpaRepository<VisitOrderHistory, Long> {
  @Query("SELECT voh FROM VisitOrderHistory voh WHERE voh.prisoner.prisonerId = :prisonerId and voh.createdTimestamp >= :fromDateTime order by voh.id asc")
  fun findVisitOrderHistoryByPrisonerIdAfterFromDateOrderByIdAsc(prisonerId: String, fromDateTime: LocalDateTime): List<VisitOrderHistory>

  @Query(
    """
    SELECT CASE WHEN COUNT(voh) > 0 THEN true ELSE false END
    FROM VisitOrderHistory voh
    JOIN voh.visitOrderHistoryAttributes attribute
    WHERE voh.prisoner.prisonerId = :prisonerId
    AND voh.type = :visitOrderHistoryType
    AND attribute.attributeType = :attributeType
    AND attribute.attributeValue = :visitReference
  """,
  )
  fun existsByPrisonerIdAndTypeAndVisitReferenceAttribute(
    prisonerId: String,
    visitOrderHistoryType: VisitOrderHistoryType,
    attributeType: VisitOrderHistoryAttributeType,
    visitReference: String,
  ): Boolean
}
