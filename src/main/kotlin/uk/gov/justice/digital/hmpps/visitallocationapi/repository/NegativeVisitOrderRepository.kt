package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder

@Repository
interface NegativeVisitOrderRepository : JpaRepository<NegativeVisitOrder, Long>
