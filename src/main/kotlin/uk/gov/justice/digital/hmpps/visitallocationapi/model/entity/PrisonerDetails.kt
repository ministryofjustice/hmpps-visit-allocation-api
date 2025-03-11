package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "PRISONER_DETAILS")
data class PrisonerDetails(

  @Id
  @Column(nullable = false)
  val prisonerId: String,

  @Column(nullable = false)
  val lastAllocatedDate: LocalDate,
)
