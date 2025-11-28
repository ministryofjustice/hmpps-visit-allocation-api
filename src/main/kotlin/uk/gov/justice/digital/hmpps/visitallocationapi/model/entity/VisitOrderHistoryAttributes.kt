package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "visit_order_history_attributes")
open class VisitOrderHistoryAttributes(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne
  @JoinColumn(name = "visit_order_history_id", nullable = false)
  var visitOrderHistory: VisitOrderHistory,

  @Column(nullable = false)
  val attributeType: String,

  @Column(nullable = false)
  val attributeValue: String,
)
