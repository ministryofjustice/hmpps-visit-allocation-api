package uk.gov.justice.digital.hmpps.visitallocationapi

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonerIncentivesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.VisitDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerRetryService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ProcessPrisonerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import java.time.LocalDate
import java.util.*

@ExtendWith(MockitoExtension::class)
class ProcessPrisonerServiceTest {

  @Mock
  private lateinit var prisonerSearchClient: PrisonerSearchClient

  @Mock
  private lateinit var incentivesClient: IncentivesClient

  @Mock
  private lateinit var prisonerDetailsService: PrisonerDetailsService

  @Mock
  private lateinit var prisonerRetryService: PrisonerRetryService

  @Mock
  private lateinit var changeLogService: ChangeLogService

  @Mock
  private lateinit var telemetryClientService: TelemetryClientService

  private lateinit var processPrisonerService: ProcessPrisonerService

  @BeforeEach
  fun setUp() {
    processPrisonerService = ProcessPrisonerService(
      prisonerSearchClient,
      incentivesClient,
      prisonerDetailsService,
      prisonerRetryService,
      changeLogService,
      telemetryClientService,
      26,
    )
  }

  /**
   * Scenario 1: Continued allocation triggered, existing STD prisoner is given VO / PVO.
   */
  @Test
  fun `Continue Allocation - Given a new prisoner has STD incentive level for HEI prison, should generate and save 2 VO and 1 PVO`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison Hewell
    val prisonerId = "AA123456"
    val prisonId = "HEI"
    val prisonerSearchResult = createPrisonerDto(prisonerId, prisonId, "IN")
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = listOf(PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))
    val changeLog = ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.BATCH_PROCESS,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "batch process run for prisoner ${dpsPrisoner.prisonerId}",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
      reference = UUID.randomUUID(),
    )

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)
    whenever(changeLogService.createLogBatchProcess(dpsPrisoner)).thenReturn(changeLog)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisonerAllocation(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
    }

    // THEN
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)
    verify(prisonerDetailsService).updatePrisonerDetails(dpsPrisoner)
  }

  /**
   * Scenario 2: Existing prisoner is given VO but isn't eligible for PVO as prison doesn't give PVO for current incentive level.
   */
  @Test
  fun `Continue Allocation - Given an existing prisoner has STD incentive level for MDI prison, should generate and save 2 VO but no PVOs`() {
    // GIVEN - An existing prisoner with Standard incentive level, in prison MDI
    val prisonerId = "AA123456"
    val prisonId = "MDI"

    val prisonerSearchResult = createPrisonerDto(prisonerId, prisonId, "IN")

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(14), null)
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = listOf(PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 0, levelCode = "STD"))
    val changeLog = ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.BATCH_PROCESS,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "batch process run for prisoner ${dpsPrisoner.prisonerId}",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
      reference = UUID.randomUUID(),
    )

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)
    whenever(changeLogService.createLogBatchProcess(dpsPrisoner)).thenReturn(changeLog)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisonerAllocation(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
    }

    // THEN - 2 Visit orders should be generated (2 VOs but no PVOs).
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisonerId)
    verify(prisonerDetailsService).updatePrisonerDetails(dpsPrisoner)
  }

  /**
   * Scenario 3: Existing prisoner is given VO but isn't eligible for PVO as it was last given within 28 days.
   */
  @Test
  fun `Continue Allocation - Given an existing prisoner has STD incentive level for MDI prison and has PVO already, should generate and save 2 VO but no PVOs`() {
    // GIVEN - An existing prisoner with Standard incentive level, in prison MDI
    val prisonerId = "AA123456"
    val prisonId = "MDI"

    val prisonerSearchResult = createPrisonerDto(prisonerId, prisonId, "IN")
    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(14), LocalDate.now().minusDays(14))
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = listOf(PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))
    val changeLog = ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.BATCH_PROCESS,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "batch process run for prisoner ${dpsPrisoner.prisonerId}",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
      reference = UUID.randomUUID(),
    )

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)
    whenever(changeLogService.createLogBatchProcess(dpsPrisoner)).thenReturn(changeLog)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisonerAllocation(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
    }

    // THEN
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisonerId)
    verify(prisonerDetailsService).updatePrisonerDetails(dpsPrisoner)
  }

  /**
   * Scenario 4: Existing prisoner is eligible for PVO as they have changed incentive level recently, but we wait for VO date before assigning.
   */
  @Test
  fun `Continue Allocation - Given an existing prisoner has ENHANCED incentive level for MDI prison and is due PVO but not VO renewal date, no VO or PVO generated`() {
    // GIVEN - An existing prisoner with Enhanced incentive level, in prison MDI
    val prisonerId = "AA123456"
    val prisonId = "MDI"

    val prisonerSearchResult = createPrisonerDto(prisonerId, prisonId, "IN")

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(10), null)
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "ENH")
    val prisonIncentiveAmounts = listOf(PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH"))

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisonerAllocation(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
    }

    // THEN
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisonerId)
    verify(prisonerDetailsService).updatePrisonerDetails(dpsPrisoner)
  }

  /**
   * Scenario 5: Existing prisoner is given VO and PVO as it was last given 14days & 28 days ago.
   */
  @Test
  fun `Continue Allocation - Given an existing prisoner has STD incentive level for MDI prison and is due VO and PVO`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison MDI
    val prisonerId = "AA123456"
    val prisonId = "MDI"

    val prisonerSearchResult = createPrisonerDto(prisonerId, prisonId, "IN")

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(14), LocalDate.now().minusDays(28))
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = listOf(PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))
    val changeLog = ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.BATCH_PROCESS,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "batch process run for prisoner ${dpsPrisoner.prisonerId}",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
      reference = UUID.randomUUID(),
    )

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)
    whenever(changeLogService.createLogBatchProcess(dpsPrisoner)).thenReturn(changeLog)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisonerAllocation(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
    }

    // THEN
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisonerId)
    verify(prisonerDetailsService).updatePrisonerDetails(dpsPrisoner)
  }

  // Prisoner VO Usage By Visit \\

  /**
   * Scenario 1: An event comes in to consume a VO and map to a visit.
   */
  @Test
  fun `Prisoner VO consumption - Given a prisoner with a balance of 2 PVO and 1 PVO, when processPrisonerVisitOrderUsage is called, then PVO is used`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison Hewell
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"
    val prisonId = "HEI"
    val visit = createVisitDto(visitReference, prisonerId, prisonId)
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    dpsPrisoner.visitOrders = mutableListOf(VisitOrder(type = VisitOrderType.PVO, status = VisitOrderStatus.AVAILABLE, prisonerId = dpsPrisoner.prisonerId, prisoner = dpsPrisoner))

    val changeLog = ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "allocated to $visitReference",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
      reference = UUID.randomUUID(),
    )

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(changeLogService.createLogAllocationUsedByVisit(dpsPrisoner, visitReference)).thenReturn(changeLog)

    // Begin test
    processPrisonerService.processPrisonerVisitOrderUsage(visit)

    // THEN
    verify(changeLogService).createLogAllocationUsedByVisit(dpsPrisoner, visitReference)
    verify(telemetryClientService).trackEvent(eq(TelemetryEventType.VO_CONSUMED_BY_VISIT), anyMap())
  }

  // Prisoner VO Refund By Visit Cancelled \\

  /**
   * Scenario 1: An event comes in to refund a VO as the visit has been cancelled.
   */
  @Test
  fun `Prisoner VO refund - Given a prisoner with a balance of 2 PVO and 1 PVO, when processPrisonerVisitOrderRefund is called, PVO is refunded`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison Hewell
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"
    val prisonId = "HEI"
    val visit = createVisitDto(visitReference, prisonerId, prisonId)
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    dpsPrisoner.visitOrders = mutableListOf(VisitOrder(type = VisitOrderType.PVO, status = VisitOrderStatus.AVAILABLE, visitReference = visitReference, prisonerId = dpsPrisoner.prisonerId, prisoner = dpsPrisoner))

    val changeLog = ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "allocated to $visitReference",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
      reference = UUID.randomUUID(),
    )

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(changeLogService.createLogAllocationRefundedByVisitCancelled(dpsPrisoner, visitReference)).thenReturn(changeLog)

    // Begin test
    processPrisonerService.processPrisonerVisitOrderRefund(visit)

    // THEN
    verify(changeLogService).createLogAllocationRefundedByVisitCancelled(dpsPrisoner, visitReference)
    verify(telemetryClientService).trackEvent(eq(TelemetryEventType.VO_REFUNDED_AFTER_VISIT_CANCELLATION), anyMap())
  }

  // Prisoner Reset balance \\

  /**
   * Scenario 1: An event comes in to reset prisoner balance (prisoner-received event), prisoner balance is reset.
   */
  @Test
  fun `Prisoner balance reset - Given a prisoner with a balance of 2 PVO and 1 PVO, when processPrisonerReceivedResetBalance is called, balance is reset`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison Hewell
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"

    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    dpsPrisoner.visitOrders = mutableListOf(VisitOrder(type = VisitOrderType.PVO, status = VisitOrderStatus.AVAILABLE, visitReference = visitReference, prisonerId = dpsPrisoner.prisonerId, prisoner = dpsPrisoner))

    val changeLog = ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.PRISONER_BALANCE_RESET,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "prisoner balance reset for reason NEW_ADMISSION",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
      reference = UUID.randomUUID(),
    )

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(changeLogService.createLogPrisonerBalanceReset(dpsPrisoner, PrisonerReceivedReasonType.NEW_ADMISSION)).thenReturn(changeLog)

    // Begin test
    processPrisonerService.processPrisonerReceivedResetBalance(prisonerId, PrisonerReceivedReasonType.NEW_ADMISSION)

    // THEN
    verify(prisonerDetailsService).updatePrisonerDetails(dpsPrisoner)
    verify(changeLogService).createLogPrisonerBalanceReset(dpsPrisoner, PrisonerReceivedReasonType.NEW_ADMISSION)
    verify(telemetryClientService).trackEvent(eq(TelemetryEventType.VO_PRISONER_BALANCE_RESET), anyMap())
    verify(changeLogService).findChangeLogForPrisonerByType(dpsPrisoner.prisonerId, ChangeLogType.PRISONER_BALANCE_RESET)
  }

  private fun createPrisonerDto(prisonerId: String, prisonId: String = "MDI", inOutStatus: String = "IN", lastPrisonId: String = "HEI"): PrisonerDto = PrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = inOutStatus, lastPrisonId = lastPrisonId)

  private fun createVisitDto(reference: String, prisonerId: String, prisonCode: String): VisitDto = VisitDto(reference, prisonerId, prisonCode)
}
