package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import java.time.LocalDateTime

@Entity
@Table(name = "CHANGE_LOG")
data class ChangeLog(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0L,

  @Column(nullable = false)
  val prisonerId: String,

  @Column(nullable = false)
  val changeTimestamp: LocalDateTime = LocalDateTime.now(),

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val changeType: ChangeLogType,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val changeSource: ChangeLogSource,

  @Column(nullable = false)
  val userId: String,

  @Column(nullable = false)
  val comment: String? = null,
)
