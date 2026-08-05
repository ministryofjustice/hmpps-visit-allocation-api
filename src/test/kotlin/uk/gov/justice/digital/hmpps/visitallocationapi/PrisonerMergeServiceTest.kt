package uk.gov.justice.digital.hmpps.visitallocationapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerMergeService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitOrderHistoryService
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VOBalancesUtil
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VisitOrdersUtil
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PrisonerMergeServiceTest {
  @Mock
  private lateinit var prisonerDetailsService: PrisonerDetailsService

  @Mock
  private lateinit var changeLogService: ChangeLogService

  @Mock
  private lateinit var visitOrderHistoryService: VisitOrderHistoryService

  @Mock
  private lateinit var voBalancesUtil: VOBalancesUtil

  @Mock
  private lateinit var telemetryClientService: TelemetryClientService

  private var visitOrdersUtil: VisitOrdersUtil = VisitOrdersUtil()

  private lateinit var prisonerMergeService: PrisonerMergeService

  @BeforeEach
  fun setUp() {
    prisonerMergeService = PrisonerMergeService(
      prisonerDetailsService,
      changeLogService,
      telemetryClientService,
      visitOrderHistoryService,
      voBalancesUtil,
      visitOrdersUtil,
    )
  }

  @Test
  fun `Given removed prisoner has more VO and PVO balance, when processPrisonerMerge is called, then missing visit orders are created`() {
    // GIVEN
    val newPrisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val newPrisonerDetails = PrisonerDetails(newPrisonerId, LocalDate.now().minusDays(14), LocalDate.now().minusDays(28))
    val removedPrisonerDetails = PrisonerDetails(removedPrisonerId, LocalDate.now().minusDays(14), null)
    val changeLog = createChangeLog(newPrisonerDetails)

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(newPrisonerId)).thenReturn(newPrisonerDetails)
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(removedPrisonerId)).thenReturn(removedPrisonerDetails)
    whenever(voBalancesUtil.getPrisonerBalance(newPrisonerDetails)).thenReturn(PrisonerBalanceDto(newPrisonerId, voBalance = 2, pvoBalance = 3))
    whenever(voBalancesUtil.getPrisonerBalance(removedPrisonerDetails)).thenReturn(PrisonerBalanceDto(removedPrisonerId, voBalance = 5, pvoBalance = 8))
    whenever(changeLogService.createLogAllocationForPrisonerMerge(newPrisonerDetails, newPrisonerId, removedPrisonerId)).thenReturn(changeLog)

    // WHEN
    val changeLogReference = prisonerMergeService.processPrisonerMerge(newPrisonerId, removedPrisonerId)

    // THEN
    assertThat(changeLogReference).isEqualTo(changeLog.reference)
    assertThat(newPrisonerDetails.visitOrders.count { it.type == VisitOrderType.VO }).isEqualTo(3)
    assertThat(newPrisonerDetails.visitOrders.count { it.type == VisitOrderType.PVO }).isEqualTo(5)
    verify(visitOrderHistoryService).logAllocationForPrisonerMerge(newPrisonerDetails, newPrisonerId, removedPrisonerId)
    verify(changeLogService).createLogAllocationForPrisonerMerge(newPrisonerDetails, newPrisonerId, removedPrisonerId)
    verify(telemetryClientService).trackEvent(eq(TelemetryEventType.VO_ADDED_POST_MERGE), anyMap())
  }

  @Test
  fun `Given removed prisoner has same or less balance, when processPrisonerMerge is called, then no visit orders are created`() {
    // GIVEN
    val newPrisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val newPrisonerDetails = PrisonerDetails(newPrisonerId, LocalDate.now().minusDays(14), null)
    val removedPrisonerDetails = PrisonerDetails(removedPrisonerId, LocalDate.now().minusDays(14), null)

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(newPrisonerId)).thenReturn(newPrisonerDetails)
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(removedPrisonerId)).thenReturn(removedPrisonerDetails)
    whenever(voBalancesUtil.getPrisonerBalance(newPrisonerDetails)).thenReturn(PrisonerBalanceDto(newPrisonerId, voBalance = 2, pvoBalance = 3))
    whenever(voBalancesUtil.getPrisonerBalance(removedPrisonerDetails)).thenReturn(PrisonerBalanceDto(removedPrisonerId, voBalance = 1, pvoBalance = 3))

    // WHEN
    val changeLogReference = prisonerMergeService.processPrisonerMerge(newPrisonerId, removedPrisonerId)

    // THEN
    assertThat(changeLogReference).isNull()
    assertThat(newPrisonerDetails.visitOrders).isEmpty()
    verify(visitOrderHistoryService, never()).logAllocationForPrisonerMerge(newPrisonerDetails, newPrisonerId, removedPrisonerId)
    verifyNoInteractions(changeLogService, telemetryClientService)
  }

  @Test
  fun `Given new prisoner does not exist, when processPrisonerMerge is called, then new prisoner is created and missing visit orders are created`() {
    // GIVEN
    val newPrisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val newPrisonerDetails = PrisonerDetails(newPrisonerId, LocalDate.now().minusDays(14), null)
    val removedPrisonerDetails = PrisonerDetails(removedPrisonerId, LocalDate.now().minusDays(14), null)
    val changeLog = createChangeLog(newPrisonerDetails)

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(newPrisonerId)).thenReturn(null)
    whenever(prisonerDetailsService.createPrisonerDetails(newPrisonerId, LocalDate.now().minusDays(14), null)).thenReturn(newPrisonerDetails)
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(removedPrisonerId)).thenReturn(removedPrisonerDetails)
    whenever(voBalancesUtil.getPrisonerBalance(newPrisonerDetails)).thenReturn(PrisonerBalanceDto(newPrisonerId, voBalance = 0, pvoBalance = 0))
    whenever(voBalancesUtil.getPrisonerBalance(removedPrisonerDetails)).thenReturn(PrisonerBalanceDto(removedPrisonerId, voBalance = 3, pvoBalance = 5))
    whenever(changeLogService.createLogAllocationForPrisonerMerge(newPrisonerDetails, newPrisonerId, removedPrisonerId)).thenReturn(changeLog)

    // WHEN
    val changeLogReference = prisonerMergeService.processPrisonerMerge(newPrisonerId, removedPrisonerId)

    // THEN
    assertThat(changeLogReference).isEqualTo(changeLog.reference)
    assertThat(newPrisonerDetails.visitOrders.count { it.type == VisitOrderType.VO }).isEqualTo(3)
    assertThat(newPrisonerDetails.visitOrders.count { it.type == VisitOrderType.PVO }).isEqualTo(5)
    verify(visitOrderHistoryService).logAllocationForPrisonerMerge(newPrisonerDetails, newPrisonerId, removedPrisonerId)
    verify(changeLogService).createLogAllocationForPrisonerMerge(newPrisonerDetails, newPrisonerId, removedPrisonerId)
    verify(telemetryClientService).trackEvent(eq(TelemetryEventType.VO_ADDED_POST_MERGE), anyMap())
  }

  @Test
  fun `Given removed prisoner does not exist, when processPrisonerMerge is called, then no visit orders are created`() {
    // GIVEN
    val newPrisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val newPrisonerDetails = PrisonerDetails(newPrisonerId, LocalDate.now().minusDays(14), null)

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(newPrisonerId)).thenReturn(newPrisonerDetails)
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(removedPrisonerId)).thenReturn(null)
    whenever(voBalancesUtil.getPrisonerBalance(newPrisonerDetails)).thenReturn(PrisonerBalanceDto(newPrisonerId, voBalance = 0, pvoBalance = 0))

    // WHEN
    val changeLogReference = prisonerMergeService.processPrisonerMerge(newPrisonerId, removedPrisonerId)

    // THEN
    assertThat(changeLogReference).isNull()
    assertThat(newPrisonerDetails.visitOrders).isEmpty()
    verify(visitOrderHistoryService, never()).logAllocationForPrisonerMerge(newPrisonerDetails, newPrisonerId, removedPrisonerId)
    verifyNoInteractions(changeLogService, telemetryClientService)
  }

  private fun createChangeLog(prisonerDetails: PrisonerDetails): ChangeLog = ChangeLog(
    changeType = ChangeLogType.ALLOCATION_ADDED_AFTER_PRISONER_MERGE,
    changeSource = ChangeLogSource.SYSTEM,
    userId = "SYSTEM",
    comment = "allocation added",
    prisoner = prisonerDetails,
    visitOrderBalance = 0,
    privilegedVisitOrderBalance = 0,
    reference = UUID.randomUUID(),
  )
}
