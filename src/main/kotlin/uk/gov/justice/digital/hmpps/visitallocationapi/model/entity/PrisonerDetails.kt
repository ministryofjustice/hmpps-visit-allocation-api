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

  fun deepCopy(): PrisonerDetails {
    val copy = PrisonerDetails(
      prisonerId = this.prisonerId,
      lastVoAllocatedDate = this.lastVoAllocatedDate,
      lastPvoAllocatedDate = this.lastPvoAllocatedDate,
    )

    // Deep copy visit orders
    copy.visitOrders = this.visitOrders.map {
      VisitOrder(
        id = it.id,
        type = it.type,
        createdTimestamp = it.createdTimestamp,
        expiryDate = it.expiryDate,
        prisonerId = it.prisonerId,
        status = it.status,
        prisoner = copy, // Point to the new copy, not the original
      )
    }.toMutableList()

    // Deep copy negative visit orders
    copy.negativeVisitOrders = this.negativeVisitOrders.map {
      NegativeVisitOrder(
        id = it.id,
        type = it.type,
        status = it.status,
        createdTimestamp = it.createdTimestamp,
        repaidDate = it.repaidDate,
        prisonerId = it.prisonerId,
        prisoner = copy,
      )
    }.toMutableList()

    // !! Don't copy changeLogs as it's lazy and might not be loaded. Also, not needed for deep copy purposes.

    return copy
  }
}
