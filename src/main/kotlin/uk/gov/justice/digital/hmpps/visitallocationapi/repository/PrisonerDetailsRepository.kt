package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails

@Repository
interface PrisonerDetailsRepository : JpaRepository<PrisonerDetails, Long> {
  @Transactional
  fun findByPrisonerId(prisonerId: String): PrisonerDetails?

  @Transactional
  @Modifying
  fun deleteByPrisonerId(prisonerId: String)
}
