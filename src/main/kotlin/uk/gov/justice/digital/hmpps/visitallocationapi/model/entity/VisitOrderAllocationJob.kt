package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.base.AbstractReferenceEntity

@Entity
@Table(name = "VISIT_ORDER_ALLOCATION_JOB")
class VisitOrderAllocationJob(
  @Column
  val totalPrisons: Int,
) : AbstractReferenceEntity()
