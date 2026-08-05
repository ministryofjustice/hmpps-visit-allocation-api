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
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerVisitOrderRefundService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitOrderHistoryService
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VisitOrdersUtil
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PrisonerVisitOrderRefundServiceTest {
  @Mock
  private lateinit var prisonerDetailsService: PrisonerDetailsService

  @Mock
  private lateinit var changeLogService: ChangeLogService

  @Mock
  private lateinit var visitOrderHistoryService: VisitOrderHistoryService

  @Mock
  private lateinit var telemetryClientService: TelemetryClientService

  private var visitOrdersUtil: VisitOrdersUtil = VisitOrdersUtil()

  private lateinit var prisonerVisitOrderRefundService: PrisonerVisitOrderRefundService

  @BeforeEach
  fun setUp() {
    prisonerVisitOrderRefundService = PrisonerVisitOrderRefundService(
      prisonerDetailsService,
      changeLogService,
      telemetryClientService,
      visitOrderHistoryService,
      visitOrdersUtil,
      26,
    )
  }

  @Test
  fun `Given a prisoner with a used PVO, when processPrisonerVisitOrderRefund is called, then PVO is refunded`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison Hewell
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"
    val prisonId = "HEI"
    val visit = createVisitDto(visitReference, prisonerId, prisonId)
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    dpsPrisoner.visitOrders.add(
      VisitOrder(
        type = VisitOrderType.PVO,
        status = VisitOrderStatus.USED,
        visitReference = visitReference,
        prisoner = dpsPrisoner,
      ),
    )

    val changeLog = ChangeLog(
      changeType = ChangeLogType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED,
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
    whenever(changeLogService.createLogAllocationRefundedByVisitCancelled(dpsPrisoner, visitReference)).thenReturn(changeLog)

    // Begin test
    prisonerVisitOrderRefundService.processPrisonerVisitOrderRefund(visit)

    // THEN
    verify(visitOrderHistoryService).logAllocationRefundedByVisitCancelled(dpsPrisoner, visitReference, VisitOrderType.PVO.name)
    verify(changeLogService).createLogAllocationRefundedByVisitCancelled(dpsPrisoner, visitReference)
    verify(telemetryClientService).trackEvent(eq(TelemetryEventType.VO_REFUNDED_AFTER_VISIT_CANCELLATION), anyMap())
  }

  @Test
  fun `Given associated VO cannot be refunded because prisoner is at VO cap, then only history is created`() {
    // GIVEN
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"
    val visit = createVisitDto(visitReference, prisonerId, "HEI")
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    repeat(26) {
      dpsPrisoner.visitOrders.add(visitOrdersUtil.createAvailableVisitOrder(dpsPrisoner, VisitOrderType.VO))
    }
    dpsPrisoner.visitOrders.add(
      VisitOrder(
        type = VisitOrderType.VO,
        status = VisitOrderStatus.USED,
        visitReference = visitReference,
        prisoner = dpsPrisoner,
      ),
    )

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(dpsPrisoner)

    // WHEN
    val changeLogReference = prisonerVisitOrderRefundService.processPrisonerVisitOrderRefund(visit)

    // THEN
    assertThat(changeLogReference).isNull()
    verify(visitOrderHistoryService).logAllocationRefundedByVisitCancelled(dpsPrisoner, visitReference, VisitOrderType.VO.name)
    verifyNoInteractions(changeLogService, telemetryClientService)
  }

  @Test
  fun `Given session template uses no visit order, when processPrisonerVisitOrderRefund is called, then only history is created`() {
    // GIVEN
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"
    val visit = createVisitDto(visitReference, prisonerId, "HEI")
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(dpsPrisoner)

    // WHEN
    val changeLogReference = prisonerVisitOrderRefundService.processPrisonerVisitOrderRefund(visit, SessionTemplateVisitOrderRestrictionType.NONE)

    // THEN
    assertThat(changeLogReference).isNull()
    verify(visitOrderHistoryService).logAllocationRefundedByVisitCancelled(dpsPrisoner, visitReference, "NONE")
    verifyNoInteractions(changeLogService, telemetryClientService)
  }

  @Test
  fun `Given refund history exists for session template using no visit order, then no extra processing is done`() {
    // GIVEN
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"
    val visit = createVisitDto(visitReference, prisonerId, "HEI")
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(dpsPrisoner)
    whenever(visitOrderHistoryService.allocationRefundedByVisitCancelledExists(prisonerId, visitReference)).thenReturn(true)

    // WHEN
    val changeLogReference = prisonerVisitOrderRefundService.processPrisonerVisitOrderRefund(visit, SessionTemplateVisitOrderRestrictionType.NONE)

    // THEN
    assertThat(changeLogReference).isNull()
    verify(visitOrderHistoryService).allocationRefundedByVisitCancelledExists(prisonerId, visitReference)
    verify(visitOrderHistoryService, never()).logAllocationRefundedByVisitCancelled(dpsPrisoner, visitReference, "NONE")
    verifyNoInteractions(changeLogService, telemetryClientService)
  }

  @Test
  fun `Given refund history exists for visit with a used VO, then no extra processing is done`() {
    // GIVEN
    val visitReference = "ab-cd-ef-gh"
    val prisonerId = "AA123456"
    val visit = createVisitDto(visitReference, prisonerId, "HEI")
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    val usedVisitOrder = VisitOrder(
      type = VisitOrderType.VO,
      status = VisitOrderStatus.USED,
      visitReference = visitReference,
      prisoner = dpsPrisoner,
    )
    dpsPrisoner.visitOrders.add(usedVisitOrder)

    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(dpsPrisoner)
    whenever(visitOrderHistoryService.allocationRefundedByVisitCancelledExists(prisonerId, visitReference)).thenReturn(true)

    // WHEN
    val changeLogReference = prisonerVisitOrderRefundService.processPrisonerVisitOrderRefund(visit)

    // THEN
    assertThat(changeLogReference).isNull()
    assertThat(usedVisitOrder.status).isEqualTo(VisitOrderStatus.USED)
    assertThat(usedVisitOrder.visitReference).isEqualTo(visitReference)
    verify(visitOrderHistoryService).allocationRefundedByVisitCancelledExists(prisonerId, visitReference)
    verify(visitOrderHistoryService, never()).logAllocationRefundedByVisitCancelled(dpsPrisoner, visitReference, VisitOrderType.VO.name)
    verifyNoInteractions(changeLogService, telemetryClientService)
  }

  private fun createVisitDto(reference: String, prisonerId: String, prisonCode: String): VisitDto = VisitDto(reference, prisonerId, prisonCode)
}
