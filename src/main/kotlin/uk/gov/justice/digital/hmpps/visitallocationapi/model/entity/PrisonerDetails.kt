package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import java.time.LocalDate

@Entity
@Table(name = "prisoner_details")
open class PrisonerDetails(
  @Id
  @Column(nullable = false)
  open val prisonerId: String,

  @Column(nullable = false)
  open var lastVoAllocatedDate: LocalDate,

  @Column(nullable = true)
  open var lastPvoAllocatedDate: LocalDate?,
) {
  @OneToMany(mappedBy = "prisoner", fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
  open val visitOrders: MutableList<VisitOrder> = mutableListOf()

  @OneToMany(mappedBy = "prisoner", fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
  open val negativeVisitOrders: MutableList<NegativeVisitOrder> = mutableListOf()

  @OneToMany(mappedBy = "prisoner", fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
  open val changeLogs: MutableList<ChangeLog> = mutableListOf()

  fun getBalance(): PrisonerBalanceDto = PrisonerBalanceDto(
    prisonerId = prisonerId,
    voBalance = getVoBalance(),
    pvoBalance = getPvoBalance(),
  )

  fun getVoBalance(): Int = this.visitOrders.count {
    it.type == VisitOrderType.VO &&
      it.status in listOf(
        VisitOrderStatus.AVAILABLE,
        VisitOrderStatus.ACCUMULATED,
      )
  }
    .minus(this.negativeVisitOrders.count { it.type == VisitOrderType.VO && it.status == NegativeVisitOrderStatus.USED })

  fun getPvoBalance(): Int = this.visitOrders.count {
    it.type == VisitOrderType.PVO &&
      it.status in listOf(
        VisitOrderStatus.AVAILABLE,
      )
  }
    .minus(this.negativeVisitOrders.count { it.type == VisitOrderType.PVO && it.status == NegativeVisitOrderStatus.USED })

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as PrisonerDetails
    return prisonerId == other.prisonerId
  }
  override fun hashCode(): Int = prisonerId.hashCode()
}
