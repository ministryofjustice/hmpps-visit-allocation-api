package uk.gov.justice.digital.hmpps.visitallocationapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.ServicePrisonDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitAllocationJobTriggerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitOrderAllocationJobService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJobSqsService

@ExtendWith(MockitoExtension::class)
class VisitAllocationJobTriggerServiceTest {
  @Mock
  private lateinit var prisonService: PrisonService

  @Mock
  private lateinit var visitOrderAllocationJobService: VisitOrderAllocationJobService

  @Mock
  private lateinit var visitAllocationEventJobSqsService: VisitAllocationEventJobSqsService

  @Mock
  private lateinit var telemetryClientService: TelemetryClientService

  private lateinit var service: VisitAllocationJobTriggerService

  @BeforeEach
  fun setUp() {
    service = VisitAllocationJobTriggerService(
      prisonService,
      visitOrderAllocationJobService,
      visitAllocationEventJobSqsService,
      telemetryClientService,
    )
  }

  @Test
  fun `creates an allocation job before sending a message for each active prison`() {
    val activePrisons = listOf(ServicePrisonDto("ABC"), ServicePrisonDto("XYZ"))
    val jobReference = "job-reference"
    whenever(prisonService.getActivePrisonsForAllocation()).thenReturn(activePrisons)
    whenever(visitOrderAllocationJobService.createAllocationJob(listOf("ABC", "XYZ"))).thenReturn(jobReference)

    val result = service.triggerVisitAllocationForActivePrisons()

    assertThat(result.allocationJobReference).isEqualTo(jobReference)
    assertThat(result.totalActivePrisons).isEqualTo(2)
    verify(visitAllocationEventJobSqsService).sendVisitAllocationEventToAllocationJobQueue(jobReference, "ABC")
    verify(visitAllocationEventJobSqsService).sendVisitAllocationEventToAllocationJobQueue(jobReference, "XYZ")
    inOrder(visitOrderAllocationJobService, visitAllocationEventJobSqsService) {
      verify(visitOrderAllocationJobService).createAllocationJob(listOf("ABC", "XYZ"))
      verify(visitAllocationEventJobSqsService).sendVisitAllocationEventToAllocationJobQueue(jobReference, "ABC")
    }
  }

  @Test
  fun `creates an empty allocation job without sending SQS messages when there are no active prisons`() {
    whenever(prisonService.getActivePrisonsForAllocation()).thenReturn(emptyList())
    whenever(visitOrderAllocationJobService.createAllocationJob(emptyList())).thenReturn("job-reference")

    val result = service.triggerVisitAllocationForActivePrisons()

    assertThat(result.totalActivePrisons).isZero()
    verify(visitAllocationEventJobSqsService, never()).sendVisitAllocationEventToAllocationJobQueue(any(), any())
  }

  @Test
  fun `continues sending messages when one SQS send fails`() {
    val activePrisons = listOf(ServicePrisonDto("ABC"), ServicePrisonDto("XYZ"))
    val jobReference = "job-reference"
    whenever(prisonService.getActivePrisonsForAllocation()).thenReturn(activePrisons)
    whenever(visitOrderAllocationJobService.createAllocationJob(listOf("ABC", "XYZ"))).thenReturn(jobReference)
    whenever(visitAllocationEventJobSqsService.sendVisitAllocationEventToAllocationJobQueue(jobReference, "ABC")).thenThrow(RuntimeException("SQS unavailable"))

    service.triggerVisitAllocationForActivePrisons()

    verify(visitAllocationEventJobSqsService, times(2)).sendVisitAllocationEventToAllocationJobQueue(any(), any())
    verify(visitAllocationEventJobSqsService).sendVisitAllocationEventToAllocationJobQueue(jobReference, "XYZ")
    verify(telemetryClientService).trackEvent(
      TelemetryEventType.PRISON_ALLOCATION_QUEUE_PUBLISH_FAILED,
      mapOf(
        "allocationJobReference" to jobReference,
        "prisonCode" to "ABC",
        "exceptionType" to "RuntimeException",
        "failureMessage" to "SQS unavailable",
      ),
    )
  }
}
