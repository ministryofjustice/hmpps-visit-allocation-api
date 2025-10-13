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
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "change_log")
open class ChangeLog(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @Column(nullable = false)
  var changeTimestamp: LocalDateTime = LocalDateTime.now(),

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var changeType: ChangeLogType,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var changeSource: ChangeLogSource,

  @Column(nullable = false)
  var userId: String,

  @Column
  var comment: String? = null,

  @Column(nullable = false)
  var visitOrderBalance: Int,

  @Column(nullable = true)
  var visitOrderAccumulatedBalance: Int? = null,

  @Column(nullable = true)
  var visitOrderAvailableBalance: Int? = null,

  @Column(nullable = true)
  var visitOrderUsedBalance: Int? = null,

  @Column(nullable = false)
  var privilegedVisitOrderBalance: Int,

  @Column(nullable = true)
  var privilegedVisitOrderAvailableBalance: Int? = null,

  @Column(nullable = true)
  var privilegedVisitOrderUsedBalance: Int? = null,

  @ManyToOne
  @JoinColumn(name = "prisoner_id", nullable = false)
  var prisoner: PrisonerDetails,

  @Column(nullable = false)
  var reference: UUID,
) {
  @get:Transient val prisonerId: String get() = prisoner.prisonerId

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as ChangeLog
    return id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()
}
