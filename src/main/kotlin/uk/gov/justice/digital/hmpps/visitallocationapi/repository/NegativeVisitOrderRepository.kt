package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder

@Repository
interface NegativeVisitOrderRepository : JpaRepository<NegativeVisitOrder, Long> {
  @Query(
    value = """
    SELECT COUNT(DISTINCT nvo.prisoner_id) AS prisoner_count
    FROM negative_visit_order AS nvo
    WHERE nvo.prisoner_id IN (:prisonerIds)
    AND nvo.status = 'USED' 
  """,
    nativeQuery = true,
  )
  fun countPrisonersWithNegativeVisitOrderBalance(@Param("prisonerIds") prisonerIds: List<String>): Long
}
