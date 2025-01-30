package uk.gov.justice.digital.hmpps.visitallocationapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderPrison
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderPrisonRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitAllocationByPrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJobSqsService

@ExtendWith(MockitoExtension::class)
class VisitAllocationByPrisonServiceTest {

  @Mock
  private lateinit var visitOrderPrisonRepository: VisitOrderPrisonRepository

  @Mock
  private lateinit var visitAllocationEventJobSqsService: VisitAllocationEventJobSqsService

  @InjectMocks
  private lateinit var visitAllocationByPrisonService: VisitAllocationByPrisonService

  @Test
  fun `Given 2 active prisons then trigger allocation sends 2 SQS messages to the allocation job queue`() {
    // given - 2 prisons are active - ABC and XYZ
    val activePrison1 = VisitOrderPrison(1, "ABC", true)
    val activePrison2 = VisitOrderPrison(2, "XYZ", true)

    // when
    whenever(visitOrderPrisonRepository.findByActive(true)).thenReturn(listOf(activePrison1, activePrison2))

    // Begin test
    visitAllocationByPrisonService.triggerAllocationByPrison()

    // then - 2 SQS messages should be sent to the allocation queue
    verify(visitAllocationEventJobSqsService, times(2)).sendVisitAllocationEventToAllocationJobQueue(any())
    verify(visitAllocationEventJobSqsService, times(1)).sendVisitAllocationEventToAllocationJobQueue(activePrison1.prisonCode)
    verify(visitAllocationEventJobSqsService, times(1)).sendVisitAllocationEventToAllocationJobQueue(activePrison2.prisonCode)
  }

  @Test
  fun `Given 0 active prisons then trigger allocation sends no SQS messages to the allocation job queue`() {
    // given - 0 prisons are active
    val activePrisons = emptyList<VisitOrderPrison>()
    // when
    whenever(visitOrderPrisonRepository.findByActive(true)).thenReturn(activePrisons)

    // Begin test
    visitAllocationByPrisonService.triggerAllocationByPrison()

    // then - no SQS messages should be sent to the allocation queue
    verify(visitAllocationEventJobSqsService, times(0)).sendVisitAllocationEventToAllocationJobQueue(any())
  }

  @Test
  fun `Given exception thrown when writing the first SQS message then trigger allocation ignores and still sends SQS message to the allocation job queue for the second prison`() {
    // given - 2 prisons are active - ABC and XYZ
    val activePrison1 = VisitOrderPrison(1, "ABC", true)
    val activePrison2 = VisitOrderPrison(2, "XYZ", true)

    // when
    whenever(visitOrderPrisonRepository.findByActive(true)).thenReturn(listOf(activePrison1, activePrison2))

    // an exception thrown when sending message for prison 1
    whenever(visitAllocationEventJobSqsService.sendVisitAllocationEventToAllocationJobQueue(activePrison1.prisonCode)).thenThrow(RuntimeException::class.java)

    // Begin test
    visitAllocationByPrisonService.triggerAllocationByPrison()

    // then - SQS messages for the second active prison is still sent
    verify(visitAllocationEventJobSqsService, times(2)).sendVisitAllocationEventToAllocationJobQueue(any())
    verify(visitAllocationEventJobSqsService, times(1)).sendVisitAllocationEventToAllocationJobQueue(activePrison1.prisonCode)
    verify(visitAllocationEventJobSqsService, times(1)).sendVisitAllocationEventToAllocationJobQueue(activePrison2.prisonCode)
  }
}
