package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderPrison

@Repository
interface VisitOrderPrisonRepository : JpaRepository<VisitOrderPrison, Long> {
  fun findByActive(active: Boolean): List<VisitOrderPrison>

  fun findByPrisonCode(prisonCode: String): VisitOrderPrison?
}
