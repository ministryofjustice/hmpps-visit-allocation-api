package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__NOMIS_API
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_GET_PRISONER_ADJUSTMENT
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerAdjustmentRequestDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerAdjustmentResponseDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callGet
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import java.time.LocalDate

@DisplayName("NomisController get prisoner adjustment tests - $VO_GET_PRISONER_ADJUSTMENT")
class NomisControllerGetAdjustmentTest : IntegrationTestBase() {

  companion object {
    const val PRISONER_ID = "AA123456"
  }

  @Test
  fun `when a request comes in to get a prisoners adjustment, then DPS service successfully returns the information`() {
    // Given
    val prisoner = entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))
    entityHelper.createChangeLog(
      ChangeLog(
        changeType = ChangeLogType.SYNC,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "Synced prisoner",
        prisonerId = prisoner.prisonerId,
        visitOrderBalance = 5,
        privilegedVisitOrderBalance = 2,
        prisoner = prisoner,
      ),
    )

    val changeLogId = entityHelper.createChangeLog(
      ChangeLog(
        changeType = ChangeLogType.SYNC,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "Synced prisoner",
        prisonerId = prisoner.prisonerId,
        visitOrderBalance = 7,
        privilegedVisitOrderBalance = 3,
        prisoner = prisoner,
      ),
    ).id

    val adjustmentRequestDto = createAdjustmentRequest(PRISONER_ID, changeLogId)

    // When
    val responseSpec = callVisitAllocationGetAdjustmentEndpoint(webTestClient, adjustmentRequestDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    val result = responseSpec.expectStatus().isOk.expectBody()
    val adjustmentResponseDto = getVisitAdjustmentResponseDto(result)
    assertAdjustmentResponse(adjustmentResponseDto = adjustmentResponseDto, expectedVoBalance = 5, expectedVoChange = 2, expectedPvoBalance = 2, expectedPvoChange = 1)
  }

  @Test
  fun `when a request comes in to get a prisoners adjustment, and only 1 entry exists in change logs, then DPS service successfully returns the information`() {
    // Given
    val prisoner = entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val changeLogId = entityHelper.createChangeLog(
      ChangeLog(
        changeType = ChangeLogType.SYNC,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "Synced prisoner",
        prisonerId = prisoner.prisonerId,
        visitOrderBalance = 7,
        privilegedVisitOrderBalance = 3,
        prisoner = prisoner,
      ),
    ).id

    val adjustmentRequestDto = createAdjustmentRequest(PRISONER_ID, changeLogId)

    // When
    val responseSpec = callVisitAllocationGetAdjustmentEndpoint(webTestClient, adjustmentRequestDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    val result = responseSpec.expectStatus().isOk.expectBody()
    val adjustmentResponseDto = getVisitAdjustmentResponseDto(result)
    assertAdjustmentResponse(adjustmentResponseDto = adjustmentResponseDto, expectedVoBalance = 0, expectedVoChange = 7, expectedPvoBalance = 0, expectedPvoChange = 3)
  }

  @Test
  fun `when a request comes in to get a prisoners adjustment, but it can't be found, then DPS service returns 404 NOT_FOUND`() {
    // Given
    val prisoner = entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val adjustmentRequestDto = createAdjustmentRequest(PRISONER_ID, 123L)

    // When
    val responseSpec = callVisitAllocationGetAdjustmentEndpoint(webTestClient, adjustmentRequestDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())
    val adjustmentRequestDto = createAdjustmentRequest("AA123456", 123L)

    // When
    val responseSpec = callVisitAllocationGetAdjustmentEndpoint(webTestClient, adjustmentRequestDto, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given no auth token

    // When
    val responseSpec = webTestClient.post().uri(getNomisPrisonerAdjustmentUrl(PRISONER_ID, "123L")).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callVisitAllocationGetAdjustmentEndpoint(
    webTestClient: WebTestClient,
    dto: VisitAllocationPrisonerAdjustmentRequestDto,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callGet(
    webTestClient,
    getNomisPrisonerAdjustmentUrl(dto.prisonerId, dto.changeLogId.toString()),
    authHttpHeaders,
  )

  private fun createAdjustmentRequest(
    prisonerId: String,
    changeLogId: Long,
  ): VisitAllocationPrisonerAdjustmentRequestDto = VisitAllocationPrisonerAdjustmentRequestDto(
    prisonerId,
    changeLogId,
  )

  private fun getNomisPrisonerAdjustmentUrl(prisonerId: String, changeLogId: String): String = VO_GET_PRISONER_ADJUSTMENT
    .replace("{prisonerId}", prisonerId)
    .replace("{adjustmentId}", changeLogId)

  private fun assertAdjustmentResponse(adjustmentResponseDto: VisitAllocationPrisonerAdjustmentResponseDto, expectedVoBalance: Int, expectedVoChange: Int, expectedPvoBalance: Int, expectedPvoChange: Int) {
    assertThat(adjustmentResponseDto.voBalance).isEqualTo(expectedVoBalance)
    assertThat(adjustmentResponseDto.changeToVoBalance).isEqualTo(expectedVoChange)

    assertThat(adjustmentResponseDto.pvoBalance).isEqualTo(expectedPvoBalance)
    assertThat(adjustmentResponseDto.changeToPvoBalance).isEqualTo(expectedPvoChange)
  }

  private fun getVisitAdjustmentResponseDto(returnResult: WebTestClient.BodyContentSpec): VisitAllocationPrisonerAdjustmentResponseDto = objectMapper.readValue(
    returnResult.returnResult().responseBody,
    VisitAllocationPrisonerAdjustmentResponseDto::class.java,
  )
}
