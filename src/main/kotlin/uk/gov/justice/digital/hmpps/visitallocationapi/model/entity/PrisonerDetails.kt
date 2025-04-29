package uk.gov.justice.digital.hmpps.visitallocationapi.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
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
}
