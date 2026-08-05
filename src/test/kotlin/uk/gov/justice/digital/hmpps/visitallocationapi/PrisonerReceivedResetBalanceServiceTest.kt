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
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeRepaymentReason
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerReceivedResetBalanceService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitOrderHistoryService
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PrisonerReceivedResetBalanceServiceTest {
  @Mock
  private lateinit var prisonerDetailsService: PrisonerDetailsService

  @Mock
  private lateinit var changeLogService: ChangeLogService

  @Mock
  private lateinit var visitOrderHistoryService: VisitOrderHistoryService

  @Mock
  private lateinit var telemetryClientService: TelemetryClientService

  private lateinit var prisonerReceivedResetBalanceService: PrisonerReceivedResetBalanceService

  @BeforeEach
  fun setUp() {
    prisonerReceivedResetBalanceService = PrisonerReceivedResetBalanceService(
      prisonerDetailsService,
      changeLogService,
      telemetryClientService,
      visitOrderHistoryService,
    )
  }

  @Test
  fun `Given a prisoner with visit orders and negative visit orders, when processPrisonerReceivedResetBalance is called, then balance is reset`() {
    // GIVEN
    val prisonerId = "AA123456"
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    dpsPrisoner.visitOrders.add(
      VisitOrder(
        type = VisitOrderType.VO,
        status = VisitOrderStatus.AVAILABLE,
        prisoner = dpsPrisoner,
      ),
    )
    dpsPrisoner.visitOrders.add(
      VisitOrder(
        type = VisitOrderType.PVO,
        status = VisitOrderStatus.ACCUMULATED,
        prisoner = dpsPrisoner,
      ),
    )
    dpsPrisoner.negativeVisitOrders.add(
      NegativeVisitOrder(
        type = VisitOrderType.VO,
        status = NegativeVisitOrderStatus.USED,
        prisoner = dpsPrisoner,
      ),
    )

    val changeLog = ChangeLog(
      changeType = ChangeLogType.PRISONER_BALANCE_RESET,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "prisoner balance reset for reason NEW_ADMISSION",
      prisoner = dpsPrisoner,
      visitOrderBalance = 0,
      privilegedVisitOrderBalance = 1,
      reference = UUID.randomUUID(),
    )

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(dpsPrisoner)
    whenever(changeLogService.createLogPrisonerBalanceReset(dpsPrisoner, PrisonerReceivedReasonType.NEW_ADMISSION)).thenReturn(changeLog)

    val changeLogReference = prisonerReceivedResetBalanceService.processPrisonerReceivedResetBalance(prisonerId, PrisonerReceivedReasonType.NEW_ADMISSION)

    // THEN
    assertThat(changeLogReference).isEqualTo(changeLog.reference)
    assertThat(dpsPrisoner.visitOrders).allMatch { it.status == VisitOrderStatus.EXPIRED && it.expiryDate == LocalDate.now() }
    assertThat(dpsPrisoner.negativeVisitOrders).allMatch {
      it.status == NegativeVisitOrderStatus.REPAID &&
        it.repaidDate == LocalDate.now() &&
        it.repaidReason == NegativeRepaymentReason.PRISONER_RECEIVED_RESET
    }
    verify(visitOrderHistoryService).logPrisonerBalanceReset(dpsPrisoner, PrisonerReceivedReasonType.NEW_ADMISSION)
    verify(changeLogService).createLogPrisonerBalanceReset(dpsPrisoner, PrisonerReceivedReasonType.NEW_ADMISSION)
    verify(telemetryClientService).trackEvent(eq(TelemetryEventType.VO_PRISONER_BALANCE_RESET), anyMap())
  }
}
