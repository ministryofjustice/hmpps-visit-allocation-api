package uk.gov.justice.digital.hmpps.visitallocationapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.SessionTemplateVisitOrderRestrictionType
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.VisitDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerVisitOrderUsageService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitOrderHistoryService
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VisitOrdersUtil
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PrisonerVisitOrderUsageServiceTest {
  @Mock
  private lateinit var prisonerDetailsService: PrisonerDetailsService

  @Mock
  private lateinit var changeLogService: ChangeLogService

  @Mock
  private lateinit var visitOrderHistoryService: VisitOrderHistoryService

  @Mock
  private lateinit var telemetryClientService: TelemetryClientService

  private var visitOrdersUtil: VisitOrdersUtil = VisitOrdersUtil()

  private lateinit var prisonerVisitOrderUsageService: PrisonerVisitOrderUsageService

  @BeforeEach
  fun setUp() {
    prisonerVisitOrderUsageService = PrisonerVisitOrderUsageService(
      prisonerDetailsService,
      changeLogService,
      telemetryClientService,
      visitOrderHistoryService,
    )
  }

  @Test
  fun `Prisoner VO consumption - Given a prisoner with a balance of 2 PVO and 1 PVO, when processPrisonerVisitOrderUsage is called, then PVO is used`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison Hewell
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"
    val prisonId = "HEI"
    val visit = createVisitDto(visitReference, prisonerId, prisonId)
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    dpsPrisoner.visitOrders.add(visitOrdersUtil.createAvailableVisitOrder(dpsPrisoner, VisitOrderType.PVO))

    val changeLog = ChangeLog(
      changeType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "allocated to $visitReference",
      prisoner = dpsPrisoner,
      visitOrderBalance = 0,
      privilegedVisitOrderBalance = 1,
      reference = UUID.randomUUID(),
    )

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(dpsPrisoner)
    whenever(changeLogService.createLogAllocationUsedByVisit(dpsPrisoner, visitReference)).thenReturn(changeLog)

    // Begin test
    prisonerVisitOrderUsageService.processPrisonerVisitOrderUsage(visit)

    // THEN
    verify(visitOrderHistoryService).logAllocationUsedByVisit(dpsPrisoner, visitReference, VisitOrderType.PVO.name)
    verify(changeLogService).createLogAllocationUsedByVisit(dpsPrisoner, visitReference)
    verify(telemetryClientService).trackEvent(eq(TelemetryEventType.VO_CONSUMED_BY_VISIT), anyMap())
  }

  @Test
  fun `Prisoner VO consumption - Given session template uses no visit order, when processPrisonerVisitOrderUsage is called, then only history is created`() {
    // GIVEN
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"
    val prisonId = "HEI"
    val visit = createVisitDto(visitReference, prisonerId, prisonId)
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    dpsPrisoner.visitOrders.add(visitOrdersUtil.createAvailableVisitOrder(dpsPrisoner, VisitOrderType.VO))

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(dpsPrisoner)

    val changeLogReference = prisonerVisitOrderUsageService.processPrisonerVisitOrderUsage(visit, SessionTemplateVisitOrderRestrictionType.NONE)

    // THEN
    assertThat(changeLogReference).isNull()
    assertThat(dpsPrisoner.visitOrders).allMatch { it.status == VisitOrderStatus.AVAILABLE && it.visitReference == null }
    assertThat(dpsPrisoner.negativeVisitOrders).isEmpty()
    verify(visitOrderHistoryService).logAllocationUsedByVisit(dpsPrisoner, visitReference, "NONE")
    verifyNoInteractions(changeLogService, telemetryClientService)
  }

  @Test
  fun `Prisoner VO consumption - Given session template uses no visit order and visit history exists, when processPrisonerVisitOrderUsage is called, then no extra processing is done`() {
    // GIVEN
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"
    val prisonId = "HEI"
    val visit = createVisitDto(visitReference, prisonerId, prisonId)
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(dpsPrisoner)
    whenever(visitOrderHistoryService.allocationUsedByVisitExists(prisonerId, visitReference)).thenReturn(true)

    val changeLogReference = prisonerVisitOrderUsageService.processPrisonerVisitOrderUsage(visit, SessionTemplateVisitOrderRestrictionType.NONE)

    // THEN
    assertThat(changeLogReference).isNull()
    verify(visitOrderHistoryService).allocationUsedByVisitExists(prisonerId, visitReference)
    verify(visitOrderHistoryService, never()).logAllocationUsedByVisit(dpsPrisoner, visitReference, "NONE")
    verifyNoInteractions(changeLogService, telemetryClientService)
  }

  private fun createVisitDto(reference: String, prisonerId: String, prisonCode: String): VisitDto = VisitDto(reference, prisonerId, prisonCode)
}
