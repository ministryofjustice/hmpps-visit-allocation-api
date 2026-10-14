package uk.gov.justice.digital.hmpps.visitallocationapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerAllocationService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitOrderHistoryService
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VisitOrdersUtil
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PrisonerAllocationServiceTest {

  @Mock
  private lateinit var prisonerDetailsService: PrisonerDetailsService

  @Mock
  private lateinit var changeLogService: ChangeLogService

  @Mock
  private lateinit var visitOrderHistoryService: VisitOrderHistoryService

  private lateinit var prisonerAllocationService: PrisonerAllocationService

  @BeforeEach
  fun setUp() {
    prisonerAllocationService = PrisonerAllocationService(
      prisonerDetailsService,
      changeLogService,
      visitOrderHistoryService,
      VisitOrdersUtil(),
      26,
    )
  }

  @Test
  fun `Continue Allocation - Given a new prisoner has STD incentive level for HEI prison, should generate and save 2 VO and 1 PVO`() {
    val prisonerId = "AA123456"
    val dpsPrisoner = PrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD")
    givenPrisonerAndChangeLog(dpsPrisoner)

    prisonerAllocationService.processPrisonerAllocation(prisonerId, prisonIncentiveAmounts, "STD")

    assertThat(dpsPrisoner.visitOrders.count { it.type == VisitOrderType.VO }).isEqualTo(2)
    assertThat(dpsPrisoner.visitOrders.count { it.type == VisitOrderType.PVO }).isEqualTo(1)
  }

  @Test
  fun `Continue Allocation - Given an existing prisoner has STD incentive level for MDI prison, should generate and save 2 VO but no PVOs`() {
    val prisonerId = "AA123456"
    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(14), null)
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 0, levelCode = "STD")
    givenPrisonerAndChangeLog(dpsPrisoner)

    prisonerAllocationService.processPrisonerAllocation(prisonerId, prisonIncentiveAmounts, "STD")

    assertThat(dpsPrisoner.visitOrders.count { it.type == VisitOrderType.VO }).isEqualTo(2)
    assertThat(dpsPrisoner.visitOrders.none { it.type == VisitOrderType.PVO }).isTrue()
  }

  @Test
  fun `Continue Allocation - Given an existing prisoner has STD incentive level for MDI prison and has PVO already, should generate and save 2 VO but no PVOs`() {
    val prisonerId = "AA123456"
    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(14), LocalDate.now().minusDays(14))
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD")
    givenPrisonerAndChangeLog(dpsPrisoner)

    prisonerAllocationService.processPrisonerAllocation(prisonerId, prisonIncentiveAmounts, "STD")

    assertThat(dpsPrisoner.visitOrders.count { it.type == VisitOrderType.VO }).isEqualTo(2)
    assertThat(dpsPrisoner.visitOrders.none { it.type == VisitOrderType.PVO }).isTrue()
  }

  @Test
  fun `Continue Allocation - Given an existing prisoner has ENHANCED incentive level for MDI prison and is due PVO but not VO renewal date, no VO or PVO generated`() {
    val prisonerId = "AA123456"
    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(10), null)
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH")
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(prisonerId)).thenReturn(dpsPrisoner)

    prisonerAllocationService.processPrisonerAllocation(prisonerId, prisonIncentiveAmounts, "ENH")

    assertThat(dpsPrisoner.visitOrders).isEmpty()
  }

  @Test
  fun `Continue Allocation - Given an existing prisoner has STD incentive level for MDI prison and is due VO and PVO`() {
    val prisonerId = "AA123456"
    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(14), LocalDate.now().minusDays(28))
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD")
    givenPrisonerAndChangeLog(dpsPrisoner)

    prisonerAllocationService.processPrisonerAllocation(prisonerId, prisonIncentiveAmounts, "STD")

    assertThat(dpsPrisoner.visitOrders.count { it.type == VisitOrderType.VO }).isEqualTo(2)
    assertThat(dpsPrisoner.visitOrders.count { it.type == VisitOrderType.PVO }).isEqualTo(1)
  }

  private fun givenPrisonerAndChangeLog(dpsPrisoner: PrisonerDetails) {
    whenever(prisonerDetailsService.getPrisonerDetailsWithLock(dpsPrisoner.prisonerId)).thenReturn(dpsPrisoner)
    whenever(changeLogService.createLogBatchProcess(dpsPrisoner)).thenReturn(
      ChangeLog(
        changeType = ChangeLogType.BATCH_PROCESS,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "batch process run for prisoner ${dpsPrisoner.prisonerId}",
        prisoner = dpsPrisoner,
        visitOrderBalance = 0,
        privilegedVisitOrderBalance = 0,
        reference = UUID.randomUUID(),
      ),
    )
  }
}
