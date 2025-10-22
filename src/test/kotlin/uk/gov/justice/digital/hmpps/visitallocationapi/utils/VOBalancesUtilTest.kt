package uk.gov.justice.digital.hmpps.visitallocationapi.utils

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

class VOBalancesUtilTest {
  private val voBalanceUtil = VOBalancesUtil()

  private fun createVisitOrders(
    visitOrderType: VisitOrderType,
    visitOrderStatus: VisitOrderStatus,
    prisonerDetails: PrisonerDetails,
    count: Int,
  ): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(times = count) {
      visitOrders.add(createVisitOrder(visitOrderType, visitOrderStatus, prisonerDetails))
    }

    return visitOrders
  }

  private fun createVisitOrder(
    visitOrderType: VisitOrderType,
    visitOrderStatus: VisitOrderStatus,
    prisonerDetails: PrisonerDetails,
  ): VisitOrder = VisitOrder(
    id = Random.nextLong(),
    type = visitOrderType,
    status = visitOrderStatus,
    createdTimestamp = LocalDateTime.now(),
    expiryDate = null,
    visitReference = null,
    prisoner = prisonerDetails,
  )

  private fun createNegativeVisitOrders(
    visitOrderType: VisitOrderType,
    negativeVisitOrderStatus: NegativeVisitOrderStatus,
    prisonerDetails: PrisonerDetails,
    count: Int,
  ): List<NegativeVisitOrder> {
    val negativeVisitOrders = mutableListOf<NegativeVisitOrder>()
    repeat(times = count) {
      negativeVisitOrders.add(createNegativeVisitOrder(visitOrderType, negativeVisitOrderStatus, prisonerDetails))
    }

    return negativeVisitOrders
  }

  private fun createNegativeVisitOrder(
    visitOrderType: VisitOrderType,
    negativeVisitOrderStatus: NegativeVisitOrderStatus,
    prisonerDetails: PrisonerDetails,
  ): NegativeVisitOrder = NegativeVisitOrder(
    id = Random.nextLong(),
    type = visitOrderType,
    status = negativeVisitOrderStatus,
    createdTimestamp = LocalDateTime.now(),
    visitReference = null,
    prisoner = prisonerDetails,
  )

  @Test
  fun `test available VO and PVO balance - negative balance`() {
    // Given
    val prisoner = mock<PrisonerDetails>()

    // prisoner has 3 available, 1 accumulated and 6 -ve VOs and 2 available Pvos
    val visitOrders = mutableListOf<VisitOrder>()
    visitOrders.addAll(createVisitOrders(VisitOrderType.VO, VisitOrderStatus.AVAILABLE, prisoner, 3))
    visitOrders.addAll(createVisitOrders(VisitOrderType.VO, VisitOrderStatus.ACCUMULATED, prisoner, 2))
    visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, prisoner, 2))

    val negativeVisitOrders = mutableListOf<NegativeVisitOrder>()
    negativeVisitOrders.addAll(createNegativeVisitOrders(VisitOrderType.VO, NegativeVisitOrderStatus.USED, prisoner, 6))

    whenever(prisoner.visitOrders).thenReturn(visitOrders)
    whenever(prisoner.negativeVisitOrders).thenReturn(negativeVisitOrders)
    whenever(prisoner.negativeVisitOrders).thenReturn(negativeVisitOrders)
    whenever(prisoner.getVoBalance()).thenReturn(-1)
    whenever(prisoner.getPvoBalance()).thenReturn(2)
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(null)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).availableVos).isEqualTo(3)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).accumulatedVos).isEqualTo(2)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).negativeVos).isEqualTo(6)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).voBalance).isEqualTo(-1)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).availablePvos).isEqualTo(2)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).negativePvos).isEqualTo(0)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).pvoBalance).isEqualTo(2)
  }

  @Test
  fun `test available VO and PVO balance - positive balance`() {
    // Given
    val prisoner = mock<PrisonerDetails>()

    // prisoner has 3 available, 5 accumulated and 3 -ve VOs and 8 available Pvos and 5 -ve Pvos
    val visitOrders = mutableListOf<VisitOrder>()
    visitOrders.addAll(createVisitOrders(VisitOrderType.VO, VisitOrderStatus.AVAILABLE, prisoner, 4))
    visitOrders.addAll(createVisitOrders(VisitOrderType.VO, VisitOrderStatus.ACCUMULATED, prisoner, 5))
    visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, prisoner, 8))

    val negativeVisitOrders = mutableListOf<NegativeVisitOrder>()
    negativeVisitOrders.addAll(createNegativeVisitOrders(VisitOrderType.VO, NegativeVisitOrderStatus.USED, prisoner, 3))
    negativeVisitOrders.addAll(createNegativeVisitOrders(VisitOrderType.PVO, NegativeVisitOrderStatus.USED, prisoner, 5))

    whenever(prisoner.visitOrders).thenReturn(visitOrders)
    whenever(prisoner.negativeVisitOrders).thenReturn(negativeVisitOrders)
    whenever(prisoner.getVoBalance()).thenReturn(6)
    whenever(prisoner.getPvoBalance()).thenReturn(3)
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(null)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).availableVos).isEqualTo(4)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).accumulatedVos).isEqualTo(5)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).negativeVos).isEqualTo(3)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).voBalance).isEqualTo(6)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).availablePvos).isEqualTo(8)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).negativePvos).isEqualTo(5)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).pvoBalance).isEqualTo(3)
  }

  @Test
  fun `test last VO allocation date and next VO allocation date when last VO allocation date is within last 14 days - same day`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastVoAllocationDate = LocalDate.now()

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(lastVoAllocationDate)
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(null)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextVoAllocationDate).isEqualTo(lastVoAllocationDate.plusDays(14))
  }

  @Test
  fun `test last VO allocation date and next VO allocation date when last VO allocation date is within last 14 days - 7 days back`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastVoAllocationDate = LocalDate.now().minusDays(7)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(lastVoAllocationDate)
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(null)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextVoAllocationDate).isEqualTo(lastVoAllocationDate.plusDays(14))
  }

  @Test
  fun `test last VO allocation date and next VO allocation date when last VO allocation date is within last 14 days - 13 days back`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastVoAllocationDate = LocalDate.now().minusDays(13)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(lastVoAllocationDate)
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(null)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextVoAllocationDate).isEqualTo(lastVoAllocationDate.plusDays(14))
  }

  @Test
  fun `test last VO allocation date and next VO allocation date when last VO allocation date is within last 14 days - 14 days back`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastVoAllocationDate = LocalDate.now().minusDays(14)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(lastVoAllocationDate)
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(null)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextVoAllocationDate).isEqualTo(LocalDate.now().plusDays(1))
  }

  @Test
  fun `test last VO allocation date and next VO allocation date when last VO allocation date is more than 14 days back - 15 days back`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastVoAllocationDate = LocalDate.now().minusDays(15)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(lastVoAllocationDate)
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(null)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextVoAllocationDate).isEqualTo(LocalDate.now().plusDays(1))
  }

  @Test
  fun `test last VO allocation date and next VO allocation date when last VO allocation date is more than 14 days back - 28 days back`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastVoAllocationDate = LocalDate.now().minusDays(28)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(lastVoAllocationDate)
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(null)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextVoAllocationDate).isEqualTo(LocalDate.now().plusDays(1))
  }

  @Test
  fun `test last VO allocation date and next VO allocation date when last VO allocation date is in the future - 1 day ahead`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastVoAllocationDate = LocalDate.now().plusDays(1)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(lastVoAllocationDate)
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(null)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextVoAllocationDate).isEqualTo(lastVoAllocationDate)
  }

  @Test
  fun `test last VO allocation date and next VO allocation date when last VO allocation date is in the future - 3 months ahead`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastVoAllocationDate = LocalDate.now().plusMonths(3)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(lastVoAllocationDate)
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(null)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextVoAllocationDate).isEqualTo(lastVoAllocationDate)
  }

  @Test
  fun `test last PVO allocation date and next PVO allocation date when last PVO allocation date is within last 28 days - same day`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastPVoAllocationDate = LocalDate.now()

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(lastPVoAllocationDate)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastPvoAllocatedDate).isEqualTo(lastPVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextPvoAllocationDate).isEqualTo(lastPVoAllocationDate.plusDays(28))
  }

  @Test
  fun `test last PVO allocation date and next PVO allocation date when last PVO allocation date is within last 28 days - 7 days back`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastPVoAllocationDate = LocalDate.now().minusDays(7)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(lastPVoAllocationDate)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastPvoAllocatedDate).isEqualTo(lastPVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextPvoAllocationDate).isEqualTo(lastPVoAllocationDate.plusDays(28))
  }

  @Test
  fun `test last PVO allocation date and next PVO allocation date when last PVO allocation date is within last 28 days - 26 days back`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastPVoAllocationDate = LocalDate.now().minusDays(26)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(lastPVoAllocationDate)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastPvoAllocatedDate).isEqualTo(lastPVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextPvoAllocationDate).isEqualTo(lastPVoAllocationDate.plusDays(28))
  }

  @Test
  fun `test last PVO allocation date and next PVO allocation date when last PVO allocation date is within last 28 days - 28 days back`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastPVoAllocationDate = LocalDate.now().minusDays(28)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(lastPVoAllocationDate)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastPvoAllocatedDate).isEqualTo(lastPVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextPvoAllocationDate).isEqualTo(LocalDate.now().plusDays(1))
  }

  @Test
  fun `test last PVO allocation date and next PVO allocation date when last PVO allocation date is more than 28 days back - 29 days back`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastPVoAllocationDate = LocalDate.now().minusDays(29)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(lastPVoAllocationDate)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastPvoAllocatedDate).isEqualTo(lastPVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextPvoAllocationDate).isEqualTo(LocalDate.now().plusDays(1))
  }

  @Test
  fun `test last PVO allocation date and next VO allocation date when last VO allocation date is more than 28 days back - 56 days back`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastPVoAllocationDate = LocalDate.now().minusDays(56)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(lastPVoAllocationDate)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastPvoAllocatedDate).isEqualTo(lastPVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextPvoAllocationDate).isEqualTo(LocalDate.now().plusDays(1))
  }

  @Test
  fun `test last PVO allocation date and next VO allocation date when last VO allocation date is in the future - 1 day ahead`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastPVoAllocationDate = LocalDate.now().plusDays(1)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(lastPVoAllocationDate)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastPvoAllocatedDate).isEqualTo(lastPVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextPvoAllocationDate).isEqualTo(lastPVoAllocationDate)
  }

  @Test
  fun `test last PVO allocation date and next VO allocation date when last VO allocation date is in the future - 3 months ahead`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastPVoAllocationDate = LocalDate.now().plusMonths(3)

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(lastPVoAllocationDate)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastPvoAllocatedDate).isEqualTo(lastPVoAllocationDate)
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextPvoAllocationDate).isEqualTo(lastPVoAllocationDate)
  }

  @Test
  fun `test last PVO allocation date and next PVO allocation date when last PVO allocation date is null`() {
    // Given
    val prisoner = mock<PrisonerDetails>()
    val lastPVoAllocationDate = null

    whenever(prisoner.visitOrders).thenReturn(mutableListOf())
    whenever(prisoner.negativeVisitOrders).thenReturn(mutableListOf())
    whenever(prisoner.lastVoAllocatedDate).thenReturn(LocalDate.now())
    whenever(prisoner.lastPvoAllocatedDate).thenReturn(lastPVoAllocationDate)
    whenever(prisoner.prisonerId).thenReturn("test")

    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).lastPvoAllocatedDate).isNull()
    Assertions.assertThat(voBalanceUtil.getPrisonersDetailedBalance(prisoner).nextPvoAllocationDate).isNull()
  }
}
