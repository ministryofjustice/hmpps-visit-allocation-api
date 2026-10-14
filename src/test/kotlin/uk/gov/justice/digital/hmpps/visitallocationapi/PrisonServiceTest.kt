package uk.gov.justice.digital.hmpps.visitallocationapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.ServicePrisonDto
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService

@ExtendWith(MockitoExtension::class)
class PrisonServiceTest {
  @Mock
  private lateinit var prisonApiClient: PrisonApiClient

  private lateinit var prisonService: PrisonService

  @BeforeEach
  fun setUp() {
    prisonService = PrisonService(prisonApiClient)
  }

  @Test
  fun `returns prisons enabled for DPS when ALL is not configured`() {
    val enabledPrisons = listOf(ServicePrisonDto("ABC"), ServicePrisonDto("XYZ"))
    whenever(prisonApiClient.getAllServicePrisonsEnabledForDps()).thenReturn(enabledPrisons)

    val result = prisonService.getActivePrisonsForAllocation()

    assertThat(result).isEqualTo(enabledPrisons)
    verify(prisonApiClient, never()).getAllActivePrisons()
  }

  @Test
  fun `returns all active prisons when ALL is configured`() {
    val activePrisons = listOf(ServicePrisonDto("ABC"), ServicePrisonDto("XYZ"))
    whenever(prisonApiClient.getAllServicePrisonsEnabledForDps()).thenReturn(listOf(ServicePrisonDto("*ALL*")))
    whenever(prisonApiClient.getAllActivePrisons()).thenReturn(activePrisons)

    val result = prisonService.getActivePrisonsForAllocation()

    assertThat(result).isEqualTo(activePrisons)
  }

  @Test
  fun `returns whether a prison is enabled for DPS`() {
    whenever(prisonApiClient.getPrisonEnabledForDps("ABC")).thenReturn(true)

    assertThat(prisonService.getPrisonEnabledForDpsByCode("ABC")).isTrue()
  }
}
