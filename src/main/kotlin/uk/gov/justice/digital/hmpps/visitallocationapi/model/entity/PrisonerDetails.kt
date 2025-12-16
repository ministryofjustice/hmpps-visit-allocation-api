package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

@Entity
@Table(name = "prisoner_details")
open class PrisonerDetails(
  @Id
  @Column(nullable = false)
  val prisonerId: String,

  @Column(nullable = false)
  var lastVoAllocatedDate: LocalDate,

  @Column(nullable = true)
  var lastPvoAllocatedDate: LocalDate?,
) {
  @OneToMany(mappedBy = "prisoner", fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
  @SQLRestriction("status <> 'EXPIRED'")
  val visitOrders: MutableList<VisitOrder> = mutableListOf()

  @OneToMany(mappedBy = "prisoner", fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
  val negativeVisitOrders: MutableList<NegativeVisitOrder> = mutableListOf()

  @OneToMany(mappedBy = "prisoner", fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
  val changeLogs: MutableList<ChangeLog> = mutableListOf()

  @OneToMany(mappedBy = "prisoner", fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
  val visitOrderHistory: MutableList<VisitOrderHistory> = mutableListOf()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as PrisonerDetails
    return prisonerId == other.prisonerId
  }
  override fun hashCode(): Int = prisonerId.hashCode()
}
