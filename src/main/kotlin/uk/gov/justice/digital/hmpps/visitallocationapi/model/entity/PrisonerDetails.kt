package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "prisoner_details")
data class PrisonerDetails(
  @Id
  @Column(nullable = false)
  val prisonerId: String,

  @Column(nullable = false)
  var lastVoAllocatedDate: LocalDate,

  @Column(nullable = true)
  var lastPvoAllocatedDate: LocalDate?,
) {
  @OneToMany(mappedBy = "prisoner", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
  var visitOrders: MutableList<VisitOrder> = mutableListOf()

  @OneToMany(mappedBy = "prisoner", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
  var negativeVisitOrders: MutableList<NegativeVisitOrder> = mutableListOf()

  @OneToMany(mappedBy = "prisoner", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  var changeLogs: MutableList<ChangeLog> = mutableListOf()
}
