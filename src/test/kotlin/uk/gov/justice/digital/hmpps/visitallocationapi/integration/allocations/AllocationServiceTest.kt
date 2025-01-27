package uk.gov.justice.digital.hmpps.visitallocationapi.integration.allocations

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
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

  @Test
  fun `Given a prisoner has STANDARD incentive level for HEI prison, should generate and save 2 VO and 1 PVO when startAllocation is called`() {
    // GIVEN - A prisoner with Standard incentive level, in prison Hewell
    val prisonerId = "AA123456"
    val prisonId = "HEI"
    val prisoner = PrisonerDto(prisonerNumber = prisonerId, prisonId = prisonId)
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1)

    // WHEN
    whenever(prisonerSearchClient.getPrisonerById(prisonerId)).thenReturn(prisoner)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerNumber)).thenReturn(prisonerIncentive)
    whenever(incentivesClient.getPrisonIncentiveLevels(prisoner.prisonId!!, prisonerIncentive.iepCode)).thenReturn(prisonIncentiveAmounts)

    // Begin test
    allocationService.startAllocation(prisonerId)

    // THEN - 3 Visit orders should be generated (2 VOs and 1 PVO).
    verify(prisonerSearchClient).getPrisonerById(prisonerId)
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisoner.prisonerNumber)
    verify(incentivesClient).getPrisonIncentiveLevels(prisoner.prisonId!!, prisonerIncentive.iepCode)

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
