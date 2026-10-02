package uk.gov.justice.digital.hmpps.visitallocationapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationJob
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationPrisonJob
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationPrisonJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.VisitOrderAllocationJobService

@ExtendWith(MockitoExtension::class)
class VisitOrderAllocationJobServiceTest {
  @Mock
  private lateinit var visitOrderAllocationJobRepository: VisitOrderAllocationJobRepository

  @Mock
  private lateinit var visitOrderAllocationPrisonJobRepository: VisitOrderAllocationPrisonJobRepository

  @InjectMocks
  private lateinit var service: VisitOrderAllocationJobService

  @Test
  fun `creates a parent allocation job and a job for each prison`() {
    val allocationJob = VisitOrderAllocationJob(totalPrisons = 2)
    whenever(visitOrderAllocationJobRepository.save(any<VisitOrderAllocationJob>())).thenReturn(allocationJob)

    val result = service.createAllocationJob(listOf("ABC", "XYZ"))

    assertThat(result).isEqualTo(allocationJob.reference)
    val prisonJobCaptor = argumentCaptor<VisitOrderAllocationPrisonJob>()
    verify(visitOrderAllocationPrisonJobRepository, times(2)).save(prisonJobCaptor.capture())
    assertThat(prisonJobCaptor.allValues.map { it.prisonCode }).containsExactly("ABC", "XYZ")
    assertThat(prisonJobCaptor.allValues.map { it.allocationJobReference }).containsOnly(allocationJob.reference)
  }
}
