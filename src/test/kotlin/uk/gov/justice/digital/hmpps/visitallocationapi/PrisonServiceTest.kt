package uk.gov.justice.digital.hmpps.visitallocationapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.ServicePrisonDto
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationJob
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationPrisonJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJobSqsService

@ExtendWith(MockitoExtension::class)
class PrisonServiceTest {
  @Mock
  private lateinit var prisonApiClient: PrisonApiClient

  @Mock
  private lateinit var visitOrderAllocationJobRepository: VisitOrderAllocationJobRepository

  @Mock
  private lateinit var visitOrderAllocationPrisonJobRepository: VisitOrderAllocationPrisonJobRepository

  @Mock
  private lateinit var visitAllocationEventJobSqsService: VisitAllocationEventJobSqsService

  private lateinit var prisonService: PrisonService

  @Test
  fun `Given 2 active prisons then trigger allocation sends 2 SQS messages to the allocation job queue`() {
    // given - 2 prisons are active - ABC and XYZ
    val activePrison1 = ServicePrisonDto("ABC", "A prison")
    val activePrison2 = ServicePrisonDto("XYZ", "A prison")
    val visitOrderAllocationJob = VisitOrderAllocationJob(totalPrisons = 2)
    val visitOrderAllocationJobReference = visitOrderAllocationJob.reference

    prisonService = PrisonService(
      prisonApiClient,
      visitOrderAllocationJobRepository,
      visitOrderAllocationPrisonJobRepository,
      visitAllocationEventJobSqsService,
      false,
    )

    // when
    whenever(prisonApiClient.getAllServicePrisonsEnabledForDps()).thenReturn(listOf(activePrison1, activePrison2))
    whenever(visitOrderAllocationJobRepository.save(any())).thenReturn(visitOrderAllocationJob)
    // Begin test
    prisonService.triggerVisitAllocationForActivePrisons()

    // then - 2 SQS messages should be sent to the allocation queue
    verify(visitAllocationEventJobSqsService, times(2)).sendVisitAllocationEventToAllocationJobQueue(any(), any())
    verify(visitAllocationEventJobSqsService, times(1)).sendVisitAllocationEventToAllocationJobQueue(visitOrderAllocationJobReference, activePrison1.agencyId)
    verify(visitAllocationEventJobSqsService, times(1)).sendVisitAllocationEventToAllocationJobQueue(visitOrderAllocationJobReference, activePrison2.agencyId)
  }

  @Test
  fun `Given 0 active prisons then trigger allocation sends no SQS messages to the allocation job queue`() {
    // given - 0 prisons are active
    val activePrisons = emptyList<ServicePrisonDto>()
    val visitOrderAllocationJob = VisitOrderAllocationJob(totalPrisons = 0)

    prisonService = PrisonService(
      prisonApiClient,
      visitOrderAllocationJobRepository,
      visitOrderAllocationPrisonJobRepository,
      visitAllocationEventJobSqsService,
      false,
    )

    // when
    whenever(prisonApiClient.getAllServicePrisonsEnabledForDps()).thenReturn(activePrisons)
    whenever(visitOrderAllocationJobRepository.save(any())).thenReturn(visitOrderAllocationJob)

    // Begin test
    prisonService.triggerVisitAllocationForActivePrisons()

    // then - no SQS messages should be sent to the allocation queue
    verify(visitAllocationEventJobSqsService, times(0)).sendVisitAllocationEventToAllocationJobQueue(any(), any())
  }

  @Test
  fun `Given exception thrown when writing the first SQS message then trigger allocation ignores and still sends SQS message to the allocation job queue for the second prison`() {
    // given - 2 prisons are active - ABC and XYZ
    val activePrison1 = ServicePrisonDto("ABC", "A prison")
    val activePrison2 = ServicePrisonDto("XYZ", "A prison")
    val visitOrderAllocationJob = VisitOrderAllocationJob(totalPrisons = 2)
    val visitOrderAllocationJobReference = visitOrderAllocationJob.reference

    prisonService = PrisonService(
      prisonApiClient,
      visitOrderAllocationJobRepository,
      visitOrderAllocationPrisonJobRepository,
      visitAllocationEventJobSqsService,
      false,
    )

    // when
    whenever(prisonApiClient.getAllServicePrisonsEnabledForDps()).thenReturn(listOf(activePrison1, activePrison2))
    whenever(visitOrderAllocationJobRepository.save(any())).thenReturn(visitOrderAllocationJob)

    // an exception thrown when sending message for prison 1
    whenever(visitAllocationEventJobSqsService.sendVisitAllocationEventToAllocationJobQueue(visitOrderAllocationJobReference, activePrison1.agencyId)).thenThrow(RuntimeException::class.java)

    // Begin test
    prisonService.triggerVisitAllocationForActivePrisons()

    // then - SQS messages for the second active prison is still sent
    verify(visitAllocationEventJobSqsService, times(2)).sendVisitAllocationEventToAllocationJobQueue(any(), any())
    verify(visitAllocationEventJobSqsService, times(1)).sendVisitAllocationEventToAllocationJobQueue(visitOrderAllocationJobReference, activePrison1.agencyId)
    verify(visitAllocationEventJobSqsService, times(1)).sendVisitAllocationEventToAllocationJobQueue(visitOrderAllocationJobReference, activePrison2.agencyId)
  }

  @Test
  fun `Given a prisoner has a special prison code and feature is enabled, then DPS owns the special code`() {
    // Given
    val specialPrisonCode = "OUT"

    prisonService = PrisonService(
      prisonApiClient,
      visitOrderAllocationJobRepository,
      visitOrderAllocationPrisonJobRepository,
      visitAllocationEventJobSqsService,
      true, // Feature enabled
    )

    // Begin test
    val response = prisonService.getPrisonEnabledForDpsByCode(specialPrisonCode)

    // Then - True is returned, dps owns the special code
    assertThat(response).isEqualTo(true)
  }

  @Test
  fun `Given a prisoner has a special prison code and feature is disabled, then NOMIS owns the special code`() {
    // Given
    val specialPrisonCode = "OUT"

    prisonService = PrisonService(
      prisonApiClient,
      visitOrderAllocationJobRepository,
      visitOrderAllocationPrisonJobRepository,
      visitAllocationEventJobSqsService,
      false, // Feature disabled
    )

    // Begin test
    val response = prisonService.getPrisonEnabledForDpsByCode(specialPrisonCode)

    // Then - False is returned, nomis owns the special code
    assertThat(response).isEqualTo(false)
  }
}
