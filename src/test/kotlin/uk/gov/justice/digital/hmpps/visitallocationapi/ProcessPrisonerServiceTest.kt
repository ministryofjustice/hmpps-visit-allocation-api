package uk.gov.justice.digital.hmpps.visitallocationapi

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonerIncentivesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerRetryService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ProcessPrisonerService
import java.time.LocalDate

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

  private lateinit var processPrisonerService: ProcessPrisonerService

  @BeforeEach
  fun setUp() {
    processPrisonerService = ProcessPrisonerService(
      prisonerSearchClient,
      incentivesClient,
      prisonerDetailsService,
      prisonerRetryService,
      26,
    )
  }

  // --- Continue Allocation Tests --- \\

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

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisoner(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
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

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisoner(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
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

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisoner(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
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
      processPrisonerService.processPrisoner(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
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

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisoner(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
    }

    // THEN
    verify(incentivesClient).getPrisonerIncentiveReviewHistory(prisonerId)
    verify(prisonerDetailsService).updatePrisonerDetails(dpsPrisoner)
  }

  // --- Accumulation --- \\

  /**
   * Scenario 1: Existing prisoner with VOs older than 28 days, has VO status updated form 'Available' to 'Accumulated'. But no VOs are expired.
   */
  @Test
  fun `Accumulation - Given an existing prisoner has existing VOs older than 28 days, they are moved to accumulated`() {
    // GIVEN - A new prisoner with Standard incentive level, in prison MDI
    val prisonerId = "AA123456"
    val prisonId = "MDI"

    val prisonerSearchResult = createPrisonerDto(prisonerId, prisonId, "IN")

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), LocalDate.now().minusDays(14))
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = listOf(PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisoner(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
    }

    // THEN
    verify(prisonerDetailsService).updatePrisonerDetails(dpsPrisoner)
  }

  // --- Expiration --- \\

  /**
   * Scenario 1: Existing prisoner with more than 26 VOs has their oldest VOs over 26 days expired and PVOs older than 28days are expired.
   */
  @Test
  fun `Expiration - Given an existing prisoner has more than 26 VOs, the extra VOs are moved to expired`() {
    // GIVEN - An existing prisoner with Standard incentive level, in prison MDI
    val prisonerId = "AA123456"
    val prisonId = "MDI"

    val prisonerSearchResult = createPrisonerDto(prisonerId, prisonId, "IN")

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(1), LocalDate.now().minusDays(14))
    val prisonerIncentive = PrisonerIncentivesDto(iepCode = "STD")
    val prisonIncentiveAmounts = listOf(PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))

    // WHEN
    whenever(prisonerDetailsService.getPrisonerDetails(prisonerId)).thenReturn(dpsPrisoner)
    whenever(prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId)).thenReturn(prisonerSearchResult)
    whenever(incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)).thenReturn(prisonerIncentive)

    // Begin test
    runBlocking {
      processPrisonerService.processPrisoner(prisonerId, "allocation-job-ref", prisonIncentiveAmounts)
    }

    // THEN
    verify(prisonerDetailsService).updatePrisonerDetails(dpsPrisoner)
  }

  private fun createPrisonerDto(prisonerId: String, prisonId: String = "MDI", inOutStatus: String = "IN", lastPrisonId: String = "HEI"): PrisonerDto = PrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = inOutStatus, lastPrisonId = lastPrisonId)
}
