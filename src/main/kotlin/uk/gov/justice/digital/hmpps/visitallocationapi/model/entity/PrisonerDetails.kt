package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "prisoner_details")
data class PrisonerDetails(
  @Id
  @Column(nullable = false)
  val prisonerId: String,

  @Column(nullable = false)
  val lastVoAllocatedDate: LocalDate,

  @Column(nullable = true)
  val lastPvoAllocatedDate: LocalDate?,
)
