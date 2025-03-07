package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import java.time.LocalDate

@Entity
@Table(name = "NEGATIVE_VISIT_ORDER")
data class NegativeVisitOrder(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0L,

  @Column(nullable = false)
  val prisonerId: String,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val status: NegativeVisitOrderStatus,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val type: NegativeVisitOrderType,

  @Column(nullable = false)
  val createdDate: LocalDate = LocalDate.now(),

  @Column(nullable = false)
  val repaidDate: LocalDate? = null,
)
