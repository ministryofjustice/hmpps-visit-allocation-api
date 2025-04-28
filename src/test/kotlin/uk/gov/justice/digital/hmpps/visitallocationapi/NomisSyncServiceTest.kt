package uk.gov.justice.digital.hmpps.visitallocationapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.service.BalanceService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class NomisSyncServiceTest {

  companion object {
    const val PRISONER_ID = "AA123456"
  }

  @Mock
  private lateinit var balanceService: BalanceService

  @Mock
  private lateinit var prisonerDetailsService: PrisonerDetailsService

  @Mock
  private lateinit var telemetryClientService: TelemetryClientService

  @Mock
  private lateinit var changeLogService: ChangeLogService

  @Mock
  private lateinit var prisonApiClient: PrisonApiClient

  @InjectMocks
  private lateinit var nomisSyncService: NomisSyncService

  // == Positive Balance paths == \\

  /**
   * Scenario 1 - Positive VO balance and PVO balance, balance increases on both
   */
  @Test
  fun `Given a prisoner with a positive vo and pvo balance, when balance increases, then vo and pvos are created and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 2, changeToVoBalance = 2, oldPvoBalance = 1, changeToPvoBalance = 1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 2, pvoBalance = 1)
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)
    existingPrisonerDetails.visitOrders.addAll(createVisitOrders(existingPrisonerDetails, VisitOrderType.VO, 2))
    existingPrisonerDetails.visitOrders.addAll(createVisitOrders(existingPrisonerDetails, VisitOrderType.PVO, 1))

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(existingPrisonerDetails)

    // WHEN
    val prisonerDetailsCaptor = argumentCaptor<PrisonerDetails>()
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(prisonerDetailsService, times(1)).updatePrisonerDetails(prisonerDetailsCaptor.capture())
    verify(changeLogService, times(1)).logSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)

    val savedPrisonerDetails = prisonerDetailsCaptor.allValues[0]
    assertThat(savedPrisonerDetails.prisonerId).isEqualTo(prisonerId)
    assertThat(savedPrisonerDetails.visitOrders.size).isEqualTo(6)
  }

  /**
   * Scenario 2 - Positive VO balance and PVO balance, balance decreases, stays above zero
   */
  @Test
  fun `Given a prisoner with a positive vo and pvo balance, when balance decreases but stays on or above zero, then vo and pvos are expired and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 2, changeToVoBalance = -1, oldPvoBalance = 1, changeToPvoBalance = -1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 2, pvoBalance = 1)
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(existingPrisonerDetails)

    // WHEN
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(changeLogService, times(1)).logSyncAdjustmentChange(any(), any())
    verify(prisonerDetailsService, times(1)).updatePrisonerDetails(any())

    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 3 - Positive VO balance and PVO balance, balance decreases, goes below zero
   */
  @Test
  fun `Given a prisoner with a positive vo and pvo balance, when balance decreases and goes below zero, then vo and pvos are expired, and negative vo and pvos are created and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 2, changeToVoBalance = -3, oldPvoBalance = 1, changeToPvoBalance = -2)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 2, pvoBalance = 1)
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)
    existingPrisonerDetails.visitOrders.addAll(createVisitOrders(existingPrisonerDetails, VisitOrderType.VO, 2))
    existingPrisonerDetails.visitOrders.addAll(createVisitOrders(existingPrisonerDetails, VisitOrderType.PVO, 1))

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(existingPrisonerDetails)

    // WHEN
    val prisonerDetailsCaptor = argumentCaptor<PrisonerDetails>()
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(prisonerDetailsService, times(1)).updatePrisonerDetails(prisonerDetailsCaptor.capture())
    verify(changeLogService, times(1)).logSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)

    val savedPrisonerDetails = prisonerDetailsCaptor.allValues[0]
    assertThat(savedPrisonerDetails.prisonerId).isEqualTo(prisonerId)
    assertThat(savedPrisonerDetails.negativeVisitOrders.size).isEqualTo(2)
  }

  // == Negative Balance paths == \\

  /**
   * Scenario 1 - Negative VO balance and PVO balance, balance decreases on both
   */
  @Test
  fun `Given a prisoner with a negative vo and pvo balance, when balance decreases, then negative vo and pvos are created and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = -2, changeToVoBalance = -2, oldPvoBalance = -1, changeToPvoBalance = -1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = -2, pvoBalance = -1)
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(existingPrisonerDetails)

    // WHEN
    val prisonerDetailsCaptor = argumentCaptor<PrisonerDetails>()
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(prisonerDetailsService, times(1)).updatePrisonerDetails(prisonerDetailsCaptor.capture())
    verify(changeLogService, times(1)).logSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)

    val savedPrisonerDetails = prisonerDetailsCaptor.allValues[0]
    assertThat(savedPrisonerDetails.prisonerId).isEqualTo(prisonerId)
    assertThat(savedPrisonerDetails.negativeVisitOrders.size).isEqualTo(3)
  }

  /**
   * Scenario 2 - Negative VO balance and PVO balance, balance increases, stays below zero
   */
  @Test
  fun `Given a prisoner with a negative vo and pvo balance, when balance increases but stays on or below zero, then negative vo and pvos are repaid and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = -2, changeToVoBalance = 1, oldPvoBalance = -1, changeToPvoBalance = 1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = -2, pvoBalance = -1)
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(existingPrisonerDetails)

    // WHEN
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(changeLogService, times(1)).logSyncAdjustmentChange(any(), any())
    verify(prisonerDetailsService, times(1)).updatePrisonerDetails(any())

    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 3 - Negative VO balance and PVO balance, balance increases, goes above zero
   */
  @Test
  fun `Given a prisoner with a negative vo and pvo balance, when balance increases and goes above zero, then all vo and pvos are repaid, and positive vo and pvos are created and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = -2, changeToVoBalance = 3, oldPvoBalance = -1, changeToPvoBalance = 2)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = -2, pvoBalance = -1)
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)
    existingPrisonerDetails.negativeVisitOrders.addAll(createNegativeVisitOrders(existingPrisonerDetails, VisitOrderType.VO, 2))
    existingPrisonerDetails.negativeVisitOrders.addAll(createNegativeVisitOrders(existingPrisonerDetails, VisitOrderType.PVO, 1))

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(existingPrisonerDetails)

    // WHEN
    val prisonerDetailsCaptor = argumentCaptor<PrisonerDetails>()
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(prisonerDetailsService, times(1)).updatePrisonerDetails(prisonerDetailsCaptor.capture())
    verify(changeLogService, times(1)).logSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)

    val savedPrisonerDetails = prisonerDetailsCaptor.allValues[0]
    assertThat(savedPrisonerDetails.prisonerId).isEqualTo(prisonerId)
    assertThat(savedPrisonerDetails.visitOrders.size).isEqualTo(2)
  }

  // == Zero Balance paths == \

  /**
   * Scenario 1 - Zero VO balance and PVO balance, balance increases on both
   */
  @Test
  fun `Given a prisoner with a zero vo and pvo balance, when balance increases, then vo and pvos are created and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 0, changeToVoBalance = 2, oldPvoBalance = 0, changeToPvoBalance = 1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 0, pvoBalance = 0)
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(existingPrisonerDetails)

    // WHEN
    val prisonerDetailsCaptor = argumentCaptor<PrisonerDetails>()
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(prisonerDetailsService, times(1)).updatePrisonerDetails(prisonerDetailsCaptor.capture())
    verify(changeLogService, times(1)).logSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)

    val savedPrisonerDetails = prisonerDetailsCaptor.allValues[0]
    assertThat(savedPrisonerDetails.prisonerId).isEqualTo(prisonerId)
    assertThat(savedPrisonerDetails.visitOrders.size).isEqualTo(3)
  }

  /**
   * Scenario 2 - Zero VO balance and PVO balance, balance decreases, negative VO / PVOs created.
   */
  @Test
  fun `Given a prisoner with a zero vo and pvo balance, when balance decreases, then negative vo and pvos are created and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 0, changeToVoBalance = -2, oldPvoBalance = 0, changeToPvoBalance = -1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 0, pvoBalance = 0)
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(existingPrisonerDetails)

    // WHEN
    val prisonerDetailsCaptor = argumentCaptor<PrisonerDetails>()
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(prisonerDetailsService, times(1)).updatePrisonerDetails(prisonerDetailsCaptor.capture())
    verify(changeLogService, times(1)).logSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)

    val savedPrisonerDetails = prisonerDetailsCaptor.allValues[0]
    assertThat(savedPrisonerDetails.prisonerId).isEqualTo(prisonerId)
    assertThat(savedPrisonerDetails.negativeVisitOrders.size).isEqualTo(3)
  }

  // Nomis Sync via events tests \\

  /**
   * Scenario 1 - Change event comes in to process, prisoner is synced.
   */
  @Test
  fun `Given a prisoner event comes through, then balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)
    existingPrisonerDetails.visitOrders.addAll(createVisitOrders(existingPrisonerDetails, VisitOrderType.VO, 2))
    existingPrisonerDetails.visitOrders.addAll(createVisitOrders(existingPrisonerDetails, VisitOrderType.PVO, 1))

    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 2, pvoBalance = 1)

    val existingNomisBalance = VisitBalancesDto(remainingVo = 3, remainingPvo = 2, latestIepAdjustDate = LocalDate.now().minusDays(1), latestPrivIepAdjustDate = LocalDate.now().minusDays(1))

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    whenever(prisonApiClient.getBookingVisitBalances(prisonerId)).thenReturn(existingNomisBalance)
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(existingPrisonerDetails)

    // WHEN
    val prisonerDetailsCaptor = argumentCaptor<PrisonerDetails>()
    nomisSyncService.syncPrisonerBalanceFromEventChange(prisonerId, DomainEventType.PRISONER_BOOKING_MOVED_EVENT_TYPE)

    // THEN
    verify(prisonerDetailsService, times(1)).updatePrisonerDetails(prisonerDetailsCaptor.capture())
    verify(changeLogService, times(1)).logSyncEventChange(any(), any())
    verifyNoInteractions(telemetryClientService)

    val savedPrisonerDetails = prisonerDetailsCaptor.allValues[0]
    assertThat(savedPrisonerDetails.prisonerId).isEqualTo(prisonerId)
    assertThat(savedPrisonerDetails.visitOrders.size).isEqualTo(5)
  }

  private fun createSyncRequest(
    prisonerId: String,
    oldVoBalance: Int,
    changeToVoBalance: Int? = null,
    oldPvoBalance: Int,
    changeToPvoBalance: Int? = null,
    createdDate: LocalDate = LocalDate.now(),
    adjustmentReasonCode: AdjustmentReasonCode = AdjustmentReasonCode.IEP,
    changeLogSource: ChangeLogSource = ChangeLogSource.SYSTEM,
    comment: String? = null,
  ): VisitAllocationPrisonerSyncDto = VisitAllocationPrisonerSyncDto(
    prisonerId,
    oldVoBalance,
    changeToVoBalance,
    oldPvoBalance,
    changeToPvoBalance,
    createdDate,
    adjustmentReasonCode,
    changeLogSource,
    comment,
  )

  private fun createVisitOrders(prisoner: PrisonerDetails, visitOrderType: VisitOrderType, amountToCreate: Int): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(amountToCreate) {
      visitOrders.add(
        VisitOrder(
          prisonerId = prisoner.prisonerId,
          type = visitOrderType,
          status = VisitOrderStatus.AVAILABLE,
          prisoner = prisoner,
        ),
      )
    }
    return visitOrders
  }

  private fun createNegativeVisitOrders(prisoner: PrisonerDetails, visitOrderType: VisitOrderType, amountToCreate: Int): List<NegativeVisitOrder> {
    val negativeVisitOrder = mutableListOf<NegativeVisitOrder>()
    repeat(amountToCreate) {
      negativeVisitOrder.add(
        NegativeVisitOrder(
          prisonerId = prisoner.prisonerId,
          type = visitOrderType,
          status = NegativeVisitOrderStatus.USED,
          prisoner = prisoner,
        ),
      )
    }
    return negativeVisitOrder
  }
}
