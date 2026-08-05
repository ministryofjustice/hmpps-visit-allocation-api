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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeRepaymentReason
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.service.AdminResetPrisonerNegativeBalanceService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitOrderHistoryService
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdminResetPrisonerNegativeBalanceServiceTest {
  @Mock
  private lateinit var prisonerDetailsService: PrisonerDetailsService

  @Mock
  private lateinit var changeLogService: ChangeLogService

  @Mock
  private lateinit var visitOrderHistoryService: VisitOrderHistoryService

  @Mock
  private lateinit var telemetryClientService: TelemetryClientService

  private lateinit var adminResetPrisonerNegativeBalanceService: AdminResetPrisonerNegativeBalanceService

  @BeforeEach
  fun setUp() {
    adminResetPrisonerNegativeBalanceService = AdminResetPrisonerNegativeBalanceService(
      prisonerDetailsService,
      changeLogService,
      telemetryClientService,
      visitOrderHistoryService,
    )
  }

  @Test
  fun `Given prisoner does not exist, when processAdminResetPrisonerNegativeBalance is called, then no processing is done`() {
    // GIVEN
    val prisonerId = "AA123456"
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(null)

    // WHEN
    val changeLogReference = adminResetPrisonerNegativeBalanceService.processAdminResetPrisonerNegativeBalance(prisonerId)

    // THEN
    assertThat(changeLogReference).isNull()
    verifyNoInteractions(changeLogService, visitOrderHistoryService, telemetryClientService)
  }

  @Test
  fun `Given prisoner has no used negative visit orders, when processAdminResetPrisonerNegativeBalance is called, then no processing is done`() {
    // GIVEN
    val prisonerId = "AA123456"
    val prisonerDetails = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    prisonerDetails.negativeVisitOrders.add(
      NegativeVisitOrder(
        type = VisitOrderType.VO,
        status = NegativeVisitOrderStatus.REPAID,
        prisoner = prisonerDetails,
      ),
    )
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(prisonerDetails)

    // WHEN
    val changeLogReference = adminResetPrisonerNegativeBalanceService.processAdminResetPrisonerNegativeBalance(prisonerId)

    // THEN
    assertThat(changeLogReference).isNull()
    assertThat(prisonerDetails.negativeVisitOrders).allMatch { it.status == NegativeVisitOrderStatus.REPAID }
    verifyNoInteractions(changeLogService, visitOrderHistoryService, telemetryClientService)
  }

  @Test
  fun `Given prisoner has used negative visit orders, when processAdminResetPrisonerNegativeBalance is called, then negative balance is reset`() {
    // GIVEN
    val prisonerId = "AA123456"
    val prisonerDetails = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    prisonerDetails.negativeVisitOrders.add(
      NegativeVisitOrder(
        type = VisitOrderType.VO,
        status = NegativeVisitOrderStatus.USED,
        prisoner = prisonerDetails,
      ),
    )
    prisonerDetails.negativeVisitOrders.add(
      NegativeVisitOrder(
        type = VisitOrderType.PVO,
        status = NegativeVisitOrderStatus.USED,
        prisoner = prisonerDetails,
      ),
    )
    val changeLog = ChangeLog(
      changeType = ChangeLogType.ADMIN_RESET_NEGATIVE_BALANCE,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "prisoners negative balance reset by admin",
      prisoner = prisonerDetails,
      visitOrderBalance = 0,
      privilegedVisitOrderBalance = 0,
      reference = UUID.randomUUID(),
    )

    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(prisonerDetails)
    whenever(changeLogService.createLogPrisonerNegativeBalanceAdminReset(prisonerDetails)).thenReturn(changeLog)

    // WHEN
    val changeLogReference = adminResetPrisonerNegativeBalanceService.processAdminResetPrisonerNegativeBalance(prisonerId)

    // THEN
    assertThat(changeLogReference).isEqualTo(changeLog.reference)
    assertThat(prisonerDetails.negativeVisitOrders).allMatch {
      it.status == NegativeVisitOrderStatus.REPAID &&
        it.repaidDate == LocalDate.now() &&
        it.repaidReason == NegativeRepaymentReason.ADMIN_RESET
    }
    verify(visitOrderHistoryService).logPrisonerNegativeBalanceAdminReset(prisonerDetails)
    verify(changeLogService).createLogPrisonerNegativeBalanceAdminReset(prisonerDetails)
    verify(telemetryClientService).trackEvent(eq(TelemetryEventType.VO_PRISONER_NEGATIVE_BALANCE_ADMIN_RESET), anyMap())
  }
}
