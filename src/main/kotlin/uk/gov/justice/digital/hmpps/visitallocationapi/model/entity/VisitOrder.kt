package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "visit_order")
data class VisitOrder(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0L,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val type: VisitOrderType,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val status: VisitOrderStatus,

  @Column(nullable = false)
  val createdTimestamp: LocalDateTime = LocalDateTime.now(),

  @Column(nullable = false)
  val expiryDate: LocalDate? = null,

  @Column(name = "prisoner_id", nullable = false)
  val prisonerId: String,

  @ManyToOne
  @JoinColumn(name = "prisoner_id", referencedColumnName = "prisonerId", insertable = false, updatable = false)
  val prisoner: PrisonerDetails,
)
