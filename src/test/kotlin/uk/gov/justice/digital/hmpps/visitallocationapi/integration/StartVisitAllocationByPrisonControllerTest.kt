package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_START_VISIT_ALLOCATION_JOB
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.jobs.VisitAllocationEventJobDto
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callPost
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderPrison
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationPrisonJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJobSqsService

@DisplayName("Start visit allocation job by prisoner")
class StartVisitAllocationByPrisonControllerTest : IntegrationTestBase() {
  @MockitoSpyBean
  private lateinit var sqsService: VisitAllocationEventJobSqsService

  @MockitoSpyBean
  private lateinit var visitOrderAllocationJobRepository: VisitOrderAllocationJobRepository

  @MockitoSpyBean
  private lateinit var visitOrderAllocationPrisonJobRepository: VisitOrderAllocationPrisonJobRepository

  private val prisonCode1 = "ABC"
  private val prisonCode2 = "XYZ"
  private val prisonCode3 = "TST"

  @Test
  fun `when allocation job started then sqs messages are sent for each active prison`() {
    // Given
    val prison1Active = VisitOrderPrison(prisonCode = prisonCode1, active = true)
    val prison2Active = VisitOrderPrison(prisonCode = prisonCode2, active = true)
    val prison3Inactive = VisitOrderPrison(prisonCode = prisonCode3, active = false)
    entityHelper.savePrison(prison1Active)
    entityHelper.savePrison(prison2Active)
    entityHelper.savePrison(prison3Inactive)

    // When
    val responseSpec = startVisitAllocationByPrisonJob(webTestClient, startVisitAllocationJobRoleHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val result = responseSpec.expectStatus().isOk.expectBody()
    val visitAllocationEventJobDto = getVisitAllocationEventJobDto(result)
    Assertions.assertThat(visitAllocationEventJobDto.totalActivePrisons).isEqualTo(2)
    verify(sqsService, times(2)).sendVisitAllocationEventToAllocationJobQueue(any(), any())
    verify(sqsService).sendVisitAllocationEventToAllocationJobQueue(visitAllocationEventJobDto.allocationJobReference, prison1Active.prisonCode)
    verify(sqsService).sendVisitAllocationEventToAllocationJobQueue(visitAllocationEventJobDto.allocationJobReference, prison2Active.prisonCode)
    verify(visitOrderAllocationJobRepository, times(1)).save(any())
    verify(visitOrderAllocationPrisonJobRepository, times(2)).save(any())
  }

  @Test
  fun `when no active prisons allocation job started then no sqs messages are sent`() {
    // Given
    val prison1Inactive = VisitOrderPrison(prisonCode = prisonCode1, active = false)
    val prison2Inactive = VisitOrderPrison(prisonCode = prisonCode2, active = false)
    val prison3Inactive = VisitOrderPrison(prisonCode = prisonCode3, active = false)
    entityHelper.savePrison(prison1Inactive)
    entityHelper.savePrison(prison2Inactive)
    entityHelper.savePrison(prison3Inactive)

    // When
    val responseSpec = startVisitAllocationByPrisonJob(webTestClient, startVisitAllocationJobRoleHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val result = responseSpec.expectStatus().isOk.expectBody()
    val visitAllocationEventJobDto = getVisitAllocationEventJobDto(result)
    Assertions.assertThat(visitAllocationEventJobDto.totalActivePrisons).isEqualTo(0)
    verify(sqsService, times(0)).sendVisitAllocationEventToAllocationJobQueue(any(), any())
    verify(visitOrderAllocationJobRepository, times(1)).save(any())
    verify(visitOrderAllocationPrisonJobRepository, times(0)).save(any())
  }

  fun startVisitAllocationByPrisonJob(
    webTestClient: WebTestClient,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): WebTestClient.ResponseSpec = callPost(webTestClient = webTestClient, url = VO_START_VISIT_ALLOCATION_JOB, authHttpHeaders = authHttpHeaders)

  private fun getVisitAllocationEventJobDto(returnResult: WebTestClient.BodyContentSpec): VisitAllocationEventJobDto = objectMapper.readValue(
    returnResult.returnResult().responseBody,
    VisitAllocationEventJobDto::class.java,
  )
}
