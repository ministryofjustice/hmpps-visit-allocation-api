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
import jakarta.persistence.Transient
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "visit_order")
open class VisitOrder(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var type: VisitOrderType,

  @Column(nullable = false) @Enumerated(EnumType.STRING)
  var status: VisitOrderStatus,

  @Column(nullable = false)
  var createdTimestamp: LocalDateTime = LocalDateTime.now(),

  @Column(nullable = false)
  var expiryDate: LocalDate? = null,

  @Column(nullable = true)
  var visitReference: String? = null,

  @ManyToOne
  @JoinColumn(name = "prisoner_id", nullable = false)
  var prisoner: PrisonerDetails,
) {
  @get:Transient
  val prisonerId: String get() = prisoner.prisonerId

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as VisitOrder
    return id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()
}
