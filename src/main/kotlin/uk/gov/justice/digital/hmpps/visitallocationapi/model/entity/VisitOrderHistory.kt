package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import java.time.LocalDateTime

@Entity
@Table(name = "visit_order_history")
class VisitOrderHistory(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @ManyToOne
  @JoinColumn(name = "prisoner_id", nullable = false)
  var prisoner: PrisonerDetails,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val type: ChangeLogType,

  @Column(nullable = false)
  var createdTimestamp: LocalDateTime = LocalDateTime.now(),

  @Column(nullable = false)
  var voBalance: Int,

  @Column(nullable = false)
  var pvoBalance: Int,

  @Column(nullable = false)
  var userName: String,

  @Column
  var comment: String? = null,

  @OneToMany(mappedBy = "visitOrderHistory", fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
  val visitOrderHistoryAttributes: MutableList<VisitOrderHistoryAttributes> = mutableListOf(),
)
