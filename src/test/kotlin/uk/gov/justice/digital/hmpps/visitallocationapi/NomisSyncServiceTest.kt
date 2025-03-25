package uk.gov.justice.digital.hmpps.visitallocationapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
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
  private lateinit var visitOrderRepository: VisitOrderRepository

  @Mock
  private lateinit var negativeVisitOrderRepository: NegativeVisitOrderRepository

  @Mock
  private lateinit var changeLogService: ChangeLogService

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

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)

    // WHEN
    val visitOrderCaptor = argumentCaptor<List<VisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the visit orders that were saved
    verify(visitOrderRepository, times(2)).saveAll(visitOrderCaptor.capture())

    // Retrieve the captured values
    val visitOrdersSaved = visitOrderCaptor.allValues[0]
    assertThat(visitOrdersSaved.size).isEqualTo(2)
    val privilegedVisitOrdersSaved = visitOrderCaptor.allValues[1]
    assertThat(privilegedVisitOrdersSaved.size).isEqualTo(1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

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
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 2, pvoBalance = 1)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)

    // WHEN
    nomisSyncService.syncPrisoner(syncDto)

    // THEN
    verify(visitOrderRepository, times(1)).expireVisitOrdersGivenAmount(prisonerId, VisitOrderType.VO, 1L)
    verify(visitOrderRepository, times(1)).expireVisitOrdersGivenAmount(prisonerId, VisitOrderType.PVO, 1L)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

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

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)

    // WHEN
    val negativeVisitOrderCaptor = argumentCaptor<List<NegativeVisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN
    verify(visitOrderRepository, times(1)).expireVisitOrdersGivenAmount(prisonerId, VisitOrderType.VO, null)
    verify(visitOrderRepository, times(1)).expireVisitOrdersGivenAmount(prisonerId, VisitOrderType.PVO, null)
    verify(negativeVisitOrderRepository, times(2)).saveAll(negativeVisitOrderCaptor.capture())

    val negativeVisitOrdersSaved = negativeVisitOrderCaptor.allValues[0]
    assertThat(negativeVisitOrdersSaved.size).isEqualTo(1)
    val negativePrivilegedVisitOrdersSaved = negativeVisitOrderCaptor.allValues[1]
    assertThat(negativePrivilegedVisitOrdersSaved.size).isEqualTo(1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    verifyNoInteractions(telemetryClientService)
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

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)

    // WHEN
    val negativeVisitOrderCaptor = argumentCaptor<List<NegativeVisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the negative visit orders that were saved
    verify(negativeVisitOrderRepository, times(2)).saveAll(negativeVisitOrderCaptor.capture())

    // Retrieve the captured values
    val negativeVisitOrdersSaved = negativeVisitOrderCaptor.allValues[0]
    assertThat(negativeVisitOrdersSaved.size).isEqualTo(2)
    val privilegedNegativeVisitOrdersSaved = negativeVisitOrderCaptor.allValues[1]
    assertThat(privilegedNegativeVisitOrdersSaved.size).isEqualTo(1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

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
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = -2, pvoBalance = -1)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)

    // WHEN
    nomisSyncService.syncPrisoner(syncDto)

    // THEN
    verify(negativeVisitOrderRepository, times(1)).repayVisitOrdersGivenAmount(prisonerId, NegativeVisitOrderType.NEGATIVE_VO, 1)
    verify(negativeVisitOrderRepository, times(1)).repayVisitOrdersGivenAmount(prisonerId, NegativeVisitOrderType.NEGATIVE_PVO, 1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

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

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)

    // WHEN
    val visitOrderCaptor = argumentCaptor<List<VisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN
    verify(negativeVisitOrderRepository, times(1)).repayVisitOrdersGivenAmount(prisonerId, NegativeVisitOrderType.NEGATIVE_VO, null)
    verify(negativeVisitOrderRepository, times(1)).repayVisitOrdersGivenAmount(prisonerId, NegativeVisitOrderType.NEGATIVE_PVO, null)
    verify(visitOrderRepository, times(2)).saveAll(visitOrderCaptor.capture())

    val visitOrdersSaved = visitOrderCaptor.allValues[0]
    assertThat(visitOrdersSaved.size).isEqualTo(1)
    val privilegedVisitOrdersSaved = visitOrderCaptor.allValues[1]
    assertThat(privilegedVisitOrdersSaved.size).isEqualTo(1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

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
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 0, pvoBalance = 0)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)

    // WHEN
    val visitOrderCaptor = argumentCaptor<List<VisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the visit orders that were saved
    verify(visitOrderRepository, times(2)).saveAll(visitOrderCaptor.capture())

    // Retrieve the captured values
    val visitOrdersSaved = visitOrderCaptor.allValues[0]
    assertThat(visitOrdersSaved.size).isEqualTo(2)
    val privilegedVisitOrdersSaved = visitOrderCaptor.allValues[1]
    assertThat(privilegedVisitOrdersSaved.size).isEqualTo(1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 2 - Positive VO balance and PVO balance, balance decreases, stays above zero
   */
  @Test
  fun `Given a prisoner with a zero vo and pvo balance, when balance decreases, then negative vo and pvos are created and balance is synced`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 0, changeToVoBalance = -2, oldPvoBalance = 0, changeToPvoBalance = -1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 0, pvoBalance = 0)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)

    // WHEN
    val negativeVisitOrderCaptor = argumentCaptor<List<NegativeVisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the negative visit orders that were saved
    verify(negativeVisitOrderRepository, times(2)).saveAll(negativeVisitOrderCaptor.capture())

    // Retrieve the captured values
    val negativeVisitOrdersSaved = negativeVisitOrderCaptor.allValues[0]
    assertThat(negativeVisitOrdersSaved.size).isEqualTo(2)
    val privilegedNegativeVisitOrdersSaved = negativeVisitOrderCaptor.allValues[1]
    assertThat(privilegedNegativeVisitOrdersSaved.size).isEqualTo(1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    verifyNoInteractions(telemetryClientService)
  }

  // == Balance out of sync paths == \\

  /**
   * Scenario 1 - Existing prisoner, NOMIS and DPS balance don't align, NOMIS balance is higher, balances are positive.
   */
  @Test
  fun `Balance out of sync - Existing Prisoner - NOMIS initially higher - balances are positive`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 3, changeToVoBalance = 2, oldPvoBalance = 2, changeToPvoBalance = 1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 2, pvoBalance = 1)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    doNothing().whenever(telemetryClientService).trackEvent(any(), any())

    // WHEN
    val visitOrderCaptor = argumentCaptor<List<VisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the visit orders that were saved
    verify(visitOrderRepository, times(2)).saveAll(visitOrderCaptor.capture())

    // Retrieve the captured values
    val visitOrdersSaved = visitOrderCaptor.allValues[0]
    assertThat(visitOrdersSaved.size).isEqualTo(3)
    val privilegedVisitOrdersSaved = visitOrderCaptor.allValues[1]
    assertThat(privilegedVisitOrdersSaved.size).isEqualTo(2)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    val voTelemetryProperties = createTelemetryVoSyncProperties(
      prisonerId,
      syncDto.oldVoBalance.toString(),
      existingPrisonerBalance.voBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, voTelemetryProperties)

    val pvoTelemetryProperties = createTelemetryPvoSyncProperties(
      prisonerId,
      syncDto.oldPvoBalance.toString(),
      existingPrisonerBalance.pvoBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, pvoTelemetryProperties)
  }

  /**
   * Scenario 2 - Existing prisoner, NOMIS and DPS balance don't align, DPS balance is higher, balances are positive.
   */
  @Test
  fun `Balance out of sync - Existing Prisoner - DPS initially higher - balances are positive`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 2, changeToVoBalance = 1, oldPvoBalance = 1, changeToPvoBalance = 1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 3, pvoBalance = 2)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    doNothing().whenever(telemetryClientService).trackEvent(any(), any())

    // WHEN
    val visitOrderCaptor = argumentCaptor<List<VisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the visit orders that were saved
    verify(visitOrderRepository, times(2)).saveAll(visitOrderCaptor.capture())

    // Retrieve the captured values
    val visitOrdersSaved = visitOrderCaptor.allValues[0]
    assertThat(visitOrdersSaved.size).isEqualTo(0)
    val privilegedVisitOrdersSaved = visitOrderCaptor.allValues[1]
    assertThat(privilegedVisitOrdersSaved.size).isEqualTo(0)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    val voTelemetryProperties = createTelemetryVoSyncProperties(
      prisonerId,
      syncDto.oldVoBalance.toString(),
      existingPrisonerBalance.voBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, voTelemetryProperties)

    val pvoTelemetryProperties = createTelemetryPvoSyncProperties(
      prisonerId,
      syncDto.oldPvoBalance.toString(),
      existingPrisonerBalance.pvoBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, pvoTelemetryProperties)
  }

  /**
   * Scenario 3 - Existing prisoner, NOMIS and DPS balance don't align, NOMIS balance is lower, balances are negative.
   */
  @Test
  fun `Balance out of sync - Existing Prisoner - NOMIS initially lower - balances are negative`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = -2, changeToVoBalance = -1, oldPvoBalance = -1, changeToPvoBalance = -1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = -1, pvoBalance = 0)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    doNothing().whenever(telemetryClientService).trackEvent(any(), any())

    // WHEN
    val negativeVisitOrderCaptor = argumentCaptor<List<NegativeVisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the visit orders that were saved
    verify(negativeVisitOrderRepository, times(2)).saveAll(negativeVisitOrderCaptor.capture())

    // Retrieve the captured values
    val visitOrdersSaved = negativeVisitOrderCaptor.allValues[0]
    assertThat(visitOrdersSaved.size).isEqualTo(2)
    val privilegedVisitOrdersSaved = negativeVisitOrderCaptor.allValues[1]
    assertThat(privilegedVisitOrdersSaved.size).isEqualTo(2)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    val voTelemetryProperties = createTelemetryVoSyncProperties(
      prisonerId,
      syncDto.oldVoBalance.toString(),
      existingPrisonerBalance.voBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, voTelemetryProperties)

    val pvoTelemetryProperties = createTelemetryPvoSyncProperties(
      prisonerId,
      syncDto.oldPvoBalance.toString(),
      existingPrisonerBalance.pvoBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, pvoTelemetryProperties)
  }

  /**
   * Scenario 4 - Existing prisoner, NOMIS and DPS balance don't align, DPS balance is lower, balances are negative.
   */
  @Test
  fun `Balance out of sync - Existing Prisoner - DPS initially lower - balances are negative`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = -2, changeToVoBalance = -1, oldPvoBalance = -1, changeToPvoBalance = -1)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = -3, pvoBalance = -2)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    doNothing().whenever(telemetryClientService).trackEvent(any(), any())

    // WHEN
    val negativeVisitOrderCaptor = argumentCaptor<List<NegativeVisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the visit orders that were saved
    verify(negativeVisitOrderRepository, times(2)).saveAll(negativeVisitOrderCaptor.capture())

    // Retrieve the captured values
    val negativeVisitOrdersSaved = negativeVisitOrderCaptor.allValues[0]
    assertThat(negativeVisitOrdersSaved.size).isEqualTo(0)
    val negativePrivilegedVisitOrdersSaved = negativeVisitOrderCaptor.allValues[1]
    assertThat(negativePrivilegedVisitOrdersSaved.size).isEqualTo(0)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    val voTelemetryProperties = createTelemetryVoSyncProperties(
      prisonerId,
      syncDto.oldVoBalance.toString(),
      existingPrisonerBalance.voBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, voTelemetryProperties)

    val pvoTelemetryProperties = createTelemetryPvoSyncProperties(
      prisonerId,
      syncDto.oldPvoBalance.toString(),
      existingPrisonerBalance.pvoBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, pvoTelemetryProperties)
  }

  /**
   * Scenario 5 - Existing prisoner, NOMIS and DPS balance don't align, NOMIS balance is lower, balances go from negative to positive.
   */
  @Test
  fun `Balance out of sync - Existing Prisoner - NOMIS initially lower - balances go from negative to positive`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = -2, changeToVoBalance = 3, oldPvoBalance = -1, changeToPvoBalance = 2)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = -1, pvoBalance = 0)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    doNothing().whenever(telemetryClientService).trackEvent(any(), any())

    // WHEN
    val visitOrderCaptor = argumentCaptor<List<VisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the visit orders that were saved
    verify(negativeVisitOrderRepository, times(1)).repayVisitOrdersGivenAmount(prisonerId, NegativeVisitOrderType.NEGATIVE_VO, null)
    verify(visitOrderRepository, times(2)).saveAll(visitOrderCaptor.capture())

    // Retrieve the captured values
    val visitOrdersSaved = visitOrderCaptor.allValues[0]
    assertThat(visitOrdersSaved.size).isEqualTo(1)
    val privilegedVisitOrdersSaved = visitOrderCaptor.allValues[1]
    assertThat(privilegedVisitOrdersSaved.size).isEqualTo(1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    val voTelemetryProperties = createTelemetryVoSyncProperties(
      prisonerId,
      syncDto.oldVoBalance.toString(),
      existingPrisonerBalance.voBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, voTelemetryProperties)

    val pvoTelemetryProperties = createTelemetryPvoSyncProperties(
      prisonerId,
      syncDto.oldPvoBalance.toString(),
      existingPrisonerBalance.pvoBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, pvoTelemetryProperties)
  }

  /**
   * Scenario 6 - Existing prisoner, NOMIS and DPS balance don't align, DPS balance is lower, balances go from negative to positive.
   */
  @Test
  fun `Balance out of sync - Existing Prisoner - DPS initially lower - balances go from negative to positive`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = -1, changeToVoBalance = 2, oldPvoBalance = -1, changeToPvoBalance = 2)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = -2, pvoBalance = -2)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    doNothing().whenever(telemetryClientService).trackEvent(any(), any())

    // WHEN
    val visitOrderCaptor = argumentCaptor<List<VisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the visit orders that were saved
    verify(negativeVisitOrderRepository, times(1)).repayVisitOrdersGivenAmount(prisonerId, NegativeVisitOrderType.NEGATIVE_VO, null)
    verify(negativeVisitOrderRepository, times(1)).repayVisitOrdersGivenAmount(prisonerId, NegativeVisitOrderType.NEGATIVE_PVO, null)
    verify(visitOrderRepository, times(2)).saveAll(visitOrderCaptor.capture())

    // Retrieve the captured values
    val visitOrdersSaved = visitOrderCaptor.allValues[0]
    assertThat(visitOrdersSaved.size).isEqualTo(1)
    val privilegedVisitOrdersSaved = visitOrderCaptor.allValues[1]
    assertThat(privilegedVisitOrdersSaved.size).isEqualTo(1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    val voTelemetryProperties = createTelemetryVoSyncProperties(
      prisonerId,
      syncDto.oldVoBalance.toString(),
      existingPrisonerBalance.voBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, voTelemetryProperties)

    val pvoTelemetryProperties = createTelemetryPvoSyncProperties(
      prisonerId,
      syncDto.oldPvoBalance.toString(),
      existingPrisonerBalance.pvoBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, pvoTelemetryProperties)
  }

  /**
   * Scenario 7 - Existing prisoner, NOMIS and DPS balance don't align, NOMIS balance is higher, balances go from positive to negative.
   */
  @Test
  fun `Balance out of sync - Existing Prisoner - NOMIS initially higher - balances go from positive to negative`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 2, changeToVoBalance = -3, oldPvoBalance = 1, changeToPvoBalance = -2)
    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 1, pvoBalance = 0)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    doNothing().whenever(telemetryClientService).trackEvent(any(), any())

    // WHEN
    val negativeVisitOrderCaptor = argumentCaptor<List<NegativeVisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the visit orders that were saved
    verify(visitOrderRepository, times(1)).expireVisitOrdersGivenAmount(prisonerId, VisitOrderType.VO, null)
    verify(negativeVisitOrderRepository, times(2)).saveAll(negativeVisitOrderCaptor.capture())

    // Retrieve the captured values
    val negativeVisitOrdersSaved = negativeVisitOrderCaptor.allValues[0]
    assertThat(negativeVisitOrdersSaved.size).isEqualTo(1)
    val negativePrivilegedVisitOrdersSaved = negativeVisitOrderCaptor.allValues[1]
    assertThat(negativePrivilegedVisitOrdersSaved.size).isEqualTo(1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    val voTelemetryProperties = createTelemetryVoSyncProperties(
      prisonerId,
      syncDto.oldVoBalance.toString(),
      existingPrisonerBalance.voBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, voTelemetryProperties)

    val pvoTelemetryProperties = createTelemetryPvoSyncProperties(
      prisonerId,
      syncDto.oldPvoBalance.toString(),
      existingPrisonerBalance.pvoBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, pvoTelemetryProperties)
  }

  /**
   * Scenario 8 - Existing prisoner, NOMIS and DPS balance don't align, DPS balance is higher, balances go from positive to negative.
   */
  @Test
  fun `Balance out of sync - Existing Prisoner - DPS initially higher - balances go from positive to negative`() {
    // GIVEN
    val prisonerId = PRISONER_ID
    val syncDto = createSyncRequest(prisonerId = prisonerId, oldVoBalance = 1, changeToVoBalance = -3, oldPvoBalance = 1, changeToPvoBalance = -2)

    val existingPrisonerBalance = PrisonerBalanceDto(prisonerId = prisonerId, voBalance = 3, pvoBalance = 2)

    // WHEN
    whenever(balanceService.getPrisonerBalance(prisonerId)).thenReturn(existingPrisonerBalance)
    doNothing().whenever(telemetryClientService).trackEvent(any(), any())

    // WHEN
    val negativeVisitOrderCaptor = argumentCaptor<List<NegativeVisitOrder>>()
    nomisSyncService.syncPrisoner(syncDto)

    // THEN - Capture the visit orders that were saved
    verify(visitOrderRepository, times(1)).expireVisitOrdersGivenAmount(prisonerId, VisitOrderType.VO, null)
    verify(visitOrderRepository, times(1)).expireVisitOrdersGivenAmount(prisonerId, VisitOrderType.PVO, null)
    verify(negativeVisitOrderRepository, times(2)).saveAll(negativeVisitOrderCaptor.capture())

    // Retrieve the captured values
    val negativeVisitOrdersSaved = negativeVisitOrderCaptor.allValues[0]
    assertThat(negativeVisitOrdersSaved.size).isEqualTo(2)
    val negativePrivilegedVisitOrdersSaved = negativeVisitOrderCaptor.allValues[1]
    assertThat(negativePrivilegedVisitOrdersSaved.size).isEqualTo(1)

    verify(changeLogService, times(1)).logSyncChange(syncDto)

    val voTelemetryProperties = createTelemetryVoSyncProperties(
      prisonerId,
      syncDto.oldVoBalance.toString(),
      existingPrisonerBalance.voBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, voTelemetryProperties)

    val pvoTelemetryProperties = createTelemetryPvoSyncProperties(
      prisonerId,
      syncDto.oldPvoBalance.toString(),
      existingPrisonerBalance.pvoBalance.toString(),
    )
    verify(telemetryClientService, times(1)).trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, pvoTelemetryProperties)
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

  private fun createTelemetryVoSyncProperties(prisonerId: String, nomisVoBalance: String, dpsVoBalance: String): Map<String, String> = mapOf(
    "prisonerId" to prisonerId,
    "nomisVoBalance" to nomisVoBalance,
    "dpsVoBalance" to dpsVoBalance,
  )

  private fun createTelemetryPvoSyncProperties(prisonerId: String, nomisPvoBalance: String, dpsPvoBalance: String): Map<String, String> = mapOf(
    "prisonerId" to prisonerId,
    "nomisPvoBalance" to nomisPvoBalance,
    "dpsPvoBalance" to dpsPvoBalance,
  )
}
