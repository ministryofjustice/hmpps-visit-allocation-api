package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "VISIT_ORDER_PRISON")
data class VisitOrderPrison(
  @Id
  @Column(nullable = false)
  val prisonCode: String,

  @Column(nullable = false)
  val active: Boolean,
)
