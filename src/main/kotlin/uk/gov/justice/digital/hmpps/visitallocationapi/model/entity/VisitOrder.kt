package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.CreatedByType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import java.time.LocalDate

@Entity
@Table(name = "VISIT_ORDER")
data class VisitOrder(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0L,

  @Column(nullable = false)
  val prisonerId: String,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val createdByType: CreatedByType,

  @Column(nullable = false)
  val createdBy: String,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val type: VisitOrderType,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val status: VisitOrderStatus,

  @Column(nullable = false, columnDefinition = "TEXT")
  val staffComment: String,

  @Column(nullable = false)
  val createdDate: LocalDate = LocalDate.now(),

  @Column(nullable = false)
  val expiryDate: LocalDate? = null,

  @Column(nullable = false)
  val outcomeReasonCode: String? = null,

  @Column(nullable = false)
  val mailedDate: LocalDate? = null,
)
