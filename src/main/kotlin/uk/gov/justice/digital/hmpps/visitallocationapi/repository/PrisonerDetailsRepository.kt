package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails

@Repository
interface PrisonerDetailsRepository : JpaRepository<PrisonerDetails, Long>
