package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_BALANCE
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callGet
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate

@DisplayName("Balance Controller tests - $VO_BALANCE")
class BalanceControllerTest : IntegrationTestBase() {

  companion object {
    const val PRISONER_ID = "AA123456"
    const val AUTH_ROLES = "ROLE_VISIT_ALLOCATION_API__NOMIS_API"
  }

  @Test
  fun `when request to get existing prisoner with positive balance, then return prisoner balance`() {
    // Given
    visitOrderRepository.save(VisitOrder(prisonerId = PRISONER_ID, type = VisitOrderType.VO, status = VisitOrderStatus.AVAILABLE))
    visitOrderRepository.save(VisitOrder(prisonerId = PRISONER_ID, type = VisitOrderType.VO, status = VisitOrderStatus.EXPIRED))
    visitOrderRepository.save(VisitOrder(prisonerId = PRISONER_ID, type = VisitOrderType.PVO, status = VisitOrderStatus.AVAILABLE))
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now(), null))

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, webTestClient, setAuthorisation(roles = listOf(AUTH_ROLES)))

    // Then
    responseSpec.expectStatus().isOk

    val prisonerBalance = getVoBalanceResponse(responseSpec)

    assertThat(prisonerBalance.voBalance).isEqualTo(1)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(1)
  }

  @Test
  fun `when request to get existing prisoner with negative balance, then return prisoner balance`() {
    // Given
    negativeVisitOrderRepository.save(NegativeVisitOrder(prisonerId = PRISONER_ID, type = NegativeVisitOrderType.NEGATIVE_VO, status = NegativeVisitOrderStatus.USED))
    negativeVisitOrderRepository.save(NegativeVisitOrder(prisonerId = PRISONER_ID, type = NegativeVisitOrderType.NEGATIVE_PVO, status = NegativeVisitOrderStatus.USED))
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now(), null))

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, webTestClient, setAuthorisation(roles = listOf(AUTH_ROLES)))

    // Then
    responseSpec.expectStatus().isOk

    val prisonerBalance = getVoBalanceResponse(responseSpec)

    assertThat(prisonerBalance.voBalance).isEqualTo(-1)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(-1)
  }

  @Test
  fun `when request to get an unknown prisoner, then status 404 NOT_FOUND is returned`() {
    // Given
    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, webTestClient, setAuthorisation(roles = listOf(AUTH_ROLES)))

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, webTestClient, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given no auth token

    // When
    val responseSpec = webTestClient.get().uri(getPrisonerBalanceUrl(PRISONER_ID)).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callVisitAllocationPrisonerBalanceEndpoint(
    prisonerId: String,
    webTestClient: WebTestClient,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callGet(
    webTestClient,
    getPrisonerBalanceUrl(prisonerId),
    authHttpHeaders,
  )

  private fun getPrisonerBalanceUrl(prisonerId: String): String = VO_BALANCE.replace("{prisonerId}", prisonerId)

  private fun getVoBalanceResponse(responseSpec: ResponseSpec): PrisonerBalanceDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, PrisonerBalanceDto::class.java)
}
