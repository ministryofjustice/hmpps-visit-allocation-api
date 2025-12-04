package uk.gov.justice.digital.hmpps.visitallocationapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitOrderHistoryService
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VOBalancesUtil
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class NomisSyncServiceTest {

  companion object {
    const val PRISONER_ID = "AA123456"
  }

  @Mock
  private lateinit var prisonerDetailsService: PrisonerDetailsService

  @Mock
  private lateinit var telemetryClientService: TelemetryClientService

  @Mock
  private lateinit var changeLogService: ChangeLogService

  @Mock
  private lateinit var visitOrderHistoryService: VisitOrderHistoryService

  @Mock
  private lateinit var prisonApiClient: PrisonApiClient

  @Mock
  private lateinit var voBalancesUtil: VOBalancesUtil

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
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)
    val prisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 2, pvoBalance = 1)

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(existingPrisonerDetails)
    whenever(voBalancesUtil.getPrisonerBalance(existingPrisonerDetails)).thenReturn(prisonerBalance)

    // WHEN
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(changeLogService, times(1)).createLogSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 2 - Positive VO balance and PVO balance, balance decreases, stays above zero
   */
  @Test
  fun `Given a prisoner with a positive vo and pvo balance, when balance decreases but stays on or above zero, then vo and pvos are expired and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 2, changeToVoBalance = -1, oldPvoBalance = 1, changeToPvoBalance = -1)

    val realDetails = PrisonerDetails(
      prisonerId = prisonerId,
      lastVoAllocatedDate = LocalDate.now().minusDays(1),
      lastPvoAllocatedDate = null,
    )
    val spyDetails = spy(realDetails)

    val existingPrisonerBalance = PrisonerBalanceDto(
      prisonerId = prisonerId,
      voBalance = 2,
      pvoBalance = 1,
    )
    doReturn(existingPrisonerBalance)
      .whenever(voBalancesUtil)
      .getPrisonerBalance(spyDetails)

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId))
      .thenReturn(spyDetails)

    // WHEN
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(changeLogService, times(1)).createLogSyncAdjustmentChange(any(), any())

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
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)
    val prisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 2, pvoBalance = 1)

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(existingPrisonerDetails)
    whenever(voBalancesUtil.getPrisonerBalance(existingPrisonerDetails)).thenReturn(prisonerBalance)

    // WHEN
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(changeLogService, times(1)).createLogSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)
  }

  // == Negative Balance paths == \\

  /**
   * Scenario 1 - Negative VO balance and PVO balance, balance decreases on both
   */
  @Test
  fun `Given a prisoner with a negative vo and pvo balance, when balance decreases, then negative vo and pvos are created and balance is synced`() {
    // GIVEN
    val prisonerId = "A1234BC"
    val syncDto = createSyncRequest(
      prisonerId = prisonerId,
      oldVoBalance = -2,
      changeToVoBalance = -2,
      oldPvoBalance = -1,
      changeToPvoBalance = -1,
    )

    val realDetails = PrisonerDetails(
      prisonerId = prisonerId,
      lastVoAllocatedDate = LocalDate.now().minusDays(1),
      lastPvoAllocatedDate = null,
    )
    val spyDetails = spy(realDetails)

    val existingPrisonerBalance = PrisonerBalanceDto(
      prisonerId = prisonerId,
      voBalance = -2,
      pvoBalance = -1,
    )
    doReturn(existingPrisonerBalance)
      .whenever(voBalancesUtil)
      .getPrisonerBalance(spyDetails)

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId))
      .thenReturn(spyDetails)

    // WHEN
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(changeLogService, times(1)).createLogSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 2 - Negative VO balance and PVO balance, balance increases, stays below zero
   */
  @Test
  fun `Given a prisoner with a negative vo and pvo balance, when balance increases but stays on or below zero, then negative vo and pvos are repaid and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = -2, changeToVoBalance = 1, oldPvoBalance = -1, changeToPvoBalance = 1)

    val realDetails = PrisonerDetails(
      prisonerId = prisonerId,
      lastVoAllocatedDate = LocalDate.now().minusDays(1),
      lastPvoAllocatedDate = null,
    )
    val spyDetails = spy(realDetails)

    val existingPrisonerBalance = PrisonerBalanceDto(
      prisonerId = prisonerId,
      voBalance = -2,
      pvoBalance = -1,
    )
    doReturn(existingPrisonerBalance)
      .whenever(voBalancesUtil)
      .getPrisonerBalance(spyDetails)

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId))
      .thenReturn(spyDetails)

    // WHEN
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(changeLogService, times(1)).createLogSyncAdjustmentChange(any(), any())

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
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)
    val prisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = -2, pvoBalance = -1)

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(existingPrisonerDetails)
    whenever(voBalancesUtil.getPrisonerBalance(existingPrisonerDetails)).thenReturn(prisonerBalance)

    // WHEN
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(changeLogService, times(1)).createLogSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)
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
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)
    val prisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 0, pvoBalance = 0)

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(existingPrisonerDetails)
    whenever(voBalancesUtil.getPrisonerBalance(existingPrisonerDetails)).thenReturn(prisonerBalance)

    // WHEN
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(changeLogService, times(1)).createLogSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 2 - Zero VO balance and PVO balance, balance decreases, negative VO / PVOs created.
   */
  @Test
  fun `Given a prisoner with a zero vo and pvo balance, when balance decreases, then negative vo and pvos are created and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 0, changeToVoBalance = -2, oldPvoBalance = 0, changeToPvoBalance = -1)
    val existingPrisonerDetails = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), lastPvoAllocatedDate = null)
    val prisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 0, pvoBalance = 0)

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(existingPrisonerDetails)
    whenever(voBalancesUtil.getPrisonerBalance(existingPrisonerDetails)).thenReturn(prisonerBalance)

    // WHEN
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)

    // THEN
    verify(changeLogService, times(1)).createLogSyncAdjustmentChange(any(), any())
    verifyNoInteractions(telemetryClientService)
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
    val prisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 2, pvoBalance = 1)

    val existingNomisBalance = VisitBalancesDto(remainingVo = 3, remainingPvo = 2, latestIepAdjustDate = LocalDate.now().minusDays(1), latestPrivIepAdjustDate = LocalDate.now().minusDays(1))

    // WHEN
    whenever(prisonApiClient.getBookingVisitBalances(prisonerId)).thenReturn(existingNomisBalance)
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(existingPrisonerDetails)
    whenever(voBalancesUtil.getPrisonerBalance(existingPrisonerDetails)).thenReturn(prisonerBalance)

    // WHEN
    nomisSyncService.syncPrisonerBalanceFromEventChange(prisonerId, DomainEventType.PRISONER_BOOKING_MOVED_EVENT_TYPE)

    // THEN
    verify(changeLogService, times(1)).createLogSyncEventChange(any(), any())
    verifyNoInteractions(telemetryClientService)
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
}
