package uk.gov.justice.digital.hmpps.visitallocationapi.integration.allocations

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argThat
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonerIncentivesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.AllocationService
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class AllocationServiceTest {

  @Mock
  private lateinit var prisonerSearchClient: PrisonerSearchClient

  @Mock
  private lateinit var incentivesClient: IncentivesClient

  @Mock
  private lateinit var visitOrderRepository: VisitOrderRepository

  @InjectMocks
  private lateinit var allocationService: AllocationService

  // --- Start Allocation Tests --- \\

  /**
   * Scenario 1: Start allocation, new prisoner is given VO / PVO for first time.
   */
  @Test
  fun `Start Allocation - Given a new prisoner has STD incentive level for HEI prison, should generate and save 2 VO and 1 PVO when startAllocation is called`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison Hewell
    val prisonerId = "AA123456"
    val prisonId = "HEI"
    val prisoner = PrisonerDto(prisonerId = prisonerId, prisonId = prisonId)
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD")

    // WHEN
    whenever(prisonerSearchClient.getPrisonerById(prisonerId)).thenReturn(prisoner)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerId)).thenReturn(prisonerIncentive)
    whenever(incentivesClient.getPrisonIncentiveLevelByLevelCode(prisoner.prisonId, prisonerIncentive.iepCode)).thenReturn(prisonIncentiveAmounts)

    // Begin test
    allocationService.startAllocation(prisonerId)

    // THEN - 3 Visit orders should be generated (2 VOs and 1 PVO).
    verify(prisonerSearchClient).getPrisonerById(prisonerId)
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisoner.prisonerId)
    verify(incentivesClient).getPrisonIncentiveLevelByLevelCode(prisoner.prisonId, prisonerIncentive.iepCode)

    verify(visitOrderRepository).saveAll(
      argThat<List<VisitOrder>> { visitOrders ->
        visitOrders.size == 3 &&
          visitOrders.count { it.type == VisitOrderType.VO } == 2 &&
          visitOrders.count { it.type == VisitOrderType.PVO } == 1 &&
          visitOrders.all { it.status == VisitOrderStatus.AVAILABLE } &&
          visitOrders.all { it.createdDate == LocalDate.now() }
      },
    )
  }

  // --- Continue Allocation Tests --- \\

  /**
   * Scenario 1: Start allocation never triggered, new prisoner is given VO / PVO for first time.
   */
  @Test
  fun `Continue Allocation - Given a new prisoner has STD incentive level for HEI prison, should generate and save 2 VO and 1 PVO`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison Hewell
    val prisonerId = "AA123456"
    val prisonId = "HEI"
    val prisoner = PrisonerDto(prisonerId = prisonerId, prisonId = prisonId)
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD")

    // WHEN
    whenever(prisonerSearchClient.getConvictedPrisonersByPrisonId(prisonId)).thenReturn(listOf(prisoner))
    whenever(incentivesClient.getPrisonIncentiveLevels(prisonId)).thenReturn(listOf(prisonIncentiveAmounts))
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerId)).thenReturn(prisonerIncentive)

    // Begin test
    allocationService.continueAllocation(prisonId)

    // THEN - 3 Visit orders should be generated (2 VOs and 1 PVO).
    verify(prisonerSearchClient).getConvictedPrisonersByPrisonId(prisonId)
    verify(incentivesClient).getPrisonIncentiveLevels(prisonId)
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisoner.prisonerId)

    verify(visitOrderRepository).saveAll(
      argThat<List<VisitOrder>> { visitOrders ->
        visitOrders.size == 3 &&
          visitOrders.count { it.type == VisitOrderType.VO } == 2 &&
          visitOrders.count { it.type == VisitOrderType.PVO } == 1 &&
          visitOrders.all { it.status == VisitOrderStatus.AVAILABLE } &&
          visitOrders.all { it.createdDate == LocalDate.now() }
      },
    )
  }

  /**
   * Scenario 2: Existing prisoner is given VO but isn't eligible for PVO as prison doesn't give PVO for current incentive level.
   */
  @Test
  fun `Continue Allocation - Given an existing prisoner has STD incentive level for MDI prison, should generate and save 2 VO but no PVOs`() {
    // GIVEN - An existing prisoner with Standard incentive level, in prison MDI
    val prisonerId = "AA123456"
    val prisonId = "MDI"
    val prisoner = PrisonerDto(prisonerId = prisonerId, prisonId = prisonId)
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 0, levelCode = "STD")

    // WHEN
    whenever(prisonerSearchClient.getConvictedPrisonersByPrisonId(prisonId)).thenReturn(listOf(prisoner))
    whenever(incentivesClient.getPrisonIncentiveLevels(prisonId)).thenReturn(listOf(prisonIncentiveAmounts))
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerId)).thenReturn(prisonerIncentive)

    // Begin test
    allocationService.continueAllocation(prisonId)

    // THEN - 2 Visit orders should be generated (2 VOs but no PVOs).
    verify(prisonerSearchClient).getConvictedPrisonersByPrisonId(prisonId)
    verify(incentivesClient).getPrisonIncentiveLevels(prisonId)
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisoner.prisonerId)

    verify(visitOrderRepository).saveAll(
      argThat<List<VisitOrder>> { visitOrders ->
        visitOrders.size == 2 &&
          visitOrders.count { it.type == VisitOrderType.VO } == 2 &&
          visitOrders.all { it.status == VisitOrderStatus.AVAILABLE } &&
          visitOrders.all { it.createdDate == LocalDate.now() }
      },
    )
  }

  /**
   * Scenario 3: Existing prisoner is given VO but isn't eligible for PVO as it was last given within 28 days.
   */
  @Test
  fun `Continue Allocation - Given an existing prisoner has STD incentive level for MDI prison and has PVO already, should generate and save 2 VO but no PVOs`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison MDI
    val prisonerId = "AA123456"
    val prisonId = "MDI"
    val prisoner = PrisonerDto(prisonerId = prisonerId, prisonId = prisonId)
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD")

    // WHEN
    whenever(prisonerSearchClient.getConvictedPrisonersByPrisonId(prisonId)).thenReturn(listOf(prisoner))
    whenever(incentivesClient.getPrisonIncentiveLevels(prisonId)).thenReturn(listOf(prisonIncentiveAmounts))
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerId)).thenReturn(prisonerIncentive)

    lenient().whenever(visitOrderRepository.findLastAllocatedDate(prisoner.prisonerId, VisitOrderType.VO)).thenReturn(LocalDate.now().minusDays(14))
    lenient().whenever(visitOrderRepository.findLastAllocatedDate(prisoner.prisonerId, VisitOrderType.PVO)).thenReturn(LocalDate.now().minusDays(14))

    // Begin test
    allocationService.continueAllocation(prisonId)

    // THEN - 2 Visit orders should be generated (2 VOs but no PVOs).
    verify(prisonerSearchClient).getConvictedPrisonersByPrisonId(prisonId)
    verify(incentivesClient).getPrisonIncentiveLevels(prisonId)
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisoner.prisonerId)

    verify(visitOrderRepository).saveAll(
      argThat<List<VisitOrder>> { visitOrders ->
        visitOrders.size == 2 &&
          visitOrders.count { it.type == VisitOrderType.VO } == 2 &&
          visitOrders.all { it.status == VisitOrderStatus.AVAILABLE } &&
          visitOrders.all { it.createdDate == LocalDate.now() }
      },
    )
  }

  /**
   * Scenario 4: Existing prisoner is eligible for PVO as they have changed incentive level recently, but we wait for VO date before assigning.
   */
  @Test
  fun `Continue Allocation - Given an existing prisoner has ENHANCED incentive level for MDI prison and has PVO already, should generate and save 2 VO but no PVOs`() {
    // GIVEN - A new prisoner with Enhanced incentive level, in prison MDI
    val prisonerId = "AA123456"
    val prisonId = "MDI"
    val prisoner = PrisonerDto(prisonerId = prisonerId, prisonId = prisonId)
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "Enhanced")
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "Enhanced")

    // WHEN
    whenever(prisonerSearchClient.getConvictedPrisonersByPrisonId(prisonId)).thenReturn(listOf(prisoner))
    whenever(incentivesClient.getPrisonIncentiveLevels(prisonId)).thenReturn(listOf(prisonIncentiveAmounts))
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerId)).thenReturn(prisonerIncentive)

    whenever(visitOrderRepository.findLastAllocatedDate(prisoner.prisonerId, VisitOrderType.VO)).thenReturn(LocalDate.now().minusDays(10))
    whenever(visitOrderRepository.findLastAllocatedDate(prisoner.prisonerId, VisitOrderType.PVO)).thenReturn(null)

    // Begin test
    allocationService.continueAllocation(prisonId)

    // THEN - 3 Visit orders should be generated (3 VOs but no PVOs).
    verify(prisonerSearchClient).getConvictedPrisonersByPrisonId(prisonId)
    verify(incentivesClient).getPrisonIncentiveLevels(prisonId)
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisoner.prisonerId)

    verify(visitOrderRepository).saveAll(
      argThat<List<VisitOrder>> { visitOrders ->
        visitOrders.isEmpty()
      },
    )
  }

  /**
   * Scenario 5: Existing prisoner is given VO and PVO as it was last given within 14days & 28 days.
   */
  @Test
  fun `Continue Allocation - Given an existing prisoner has STD incentive level for MDI prison and is due VO and PVO`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison MDI
    val prisonerId = "AA123456"
    val prisonId = "MDI"
    val prisoner = PrisonerDto(prisonerId = prisonerId, prisonId = prisonId)
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD")

    // WHEN
    whenever(prisonerSearchClient.getConvictedPrisonersByPrisonId(prisonId)).thenReturn(listOf(prisoner))
    whenever(incentivesClient.getPrisonIncentiveLevels(prisonId)).thenReturn(listOf(prisonIncentiveAmounts))
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerId)).thenReturn(prisonerIncentive)

    lenient().whenever(visitOrderRepository.findLastAllocatedDate(prisoner.prisonerId, VisitOrderType.VO)).thenReturn(LocalDate.now().minusDays(14))
    lenient().whenever(visitOrderRepository.findLastAllocatedDate(prisoner.prisonerId, VisitOrderType.PVO)).thenReturn(LocalDate.now().minusDays(28))

    // Begin test
    allocationService.continueAllocation(prisonId)

    // THEN - 2 Visit orders should be generated (2 VOs but no PVOs).
    verify(prisonerSearchClient).getConvictedPrisonersByPrisonId(prisonId)
    verify(incentivesClient).getPrisonIncentiveLevels(prisonId)
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisoner.prisonerId)

    verify(visitOrderRepository).saveAll(
      argThat<List<VisitOrder>> { visitOrders ->
        visitOrders.size == 3 &&
          visitOrders.count { it.type == VisitOrderType.VO } == 2 &&
          visitOrders.count { it.type == VisitOrderType.PVO } == 1 &&
          visitOrders.all { it.status == VisitOrderStatus.AVAILABLE } &&
          visitOrders.all { it.createdDate == LocalDate.now() }
      },
    )
  }
}
