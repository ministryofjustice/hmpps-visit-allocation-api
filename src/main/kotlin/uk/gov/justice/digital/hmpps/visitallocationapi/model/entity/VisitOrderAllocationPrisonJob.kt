package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.base.AbstractIdEntity
import java.time.LocalDateTime

@Entity
@Table(name = "VISIT_ORDER_ALLOCATION_PRISON_JOB")
class VisitOrderAllocationPrisonJob(
  @Column(nullable = false)
  val allocationJobReference: String,

  @Column(nullable = false)
  val prisonCode: String,

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime? = null,

  @Column()
  val startTimestamp: LocalDateTime? = null,

  @Column(nullable = false)
  val failureMessage: String? = null,

  @Column(nullable = false)
  val convictedPrisoners: Int? = null,

  @Column(nullable = false)
  val processedPrisoners: Int? = null,

  @Column(nullable = false)
  val failedPrisoners: Int? = null,

  @Column
  val endTimestamp: LocalDateTime? = null,
) : AbstractIdEntity()
