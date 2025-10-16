package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__ADMIN
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.admin.RESET_NEGATIVE_VO_BALANCE
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.admin.RESET_NEGATIVE_VO_BALANCE_COUNT
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.admin.PrisonNegativeBalanceCountDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callGet
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import java.time.LocalDate

@DisplayName("Admin Controller tests - $RESET_NEGATIVE_VO_BALANCE")
class AdminControllerCountNegativeBalancesTest : IntegrationTestBase() {

  @Test
  fun `when admin request to find count of negative balances for prison comes in, count is returned`() {
    // Given
    val prisonCode = "HEI"
    val prisonerAId = "AA123456"
    val prisonerBId = "BB123456"
    val prisonerA = prisonerDetailsRepository.save(PrisonerDetails(prisonerId = prisonerAId, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.VO, status = NegativeVisitOrderStatus.USED, prisoner = prisonerA))
    val prisonerB = prisonerDetailsRepository.save(PrisonerDetails(prisonerId = prisonerBId, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.VO, status = NegativeVisitOrderStatus.USED, prisoner = prisonerB))

    // When
    val prisonerSearchResult = listOf(createPrisonerDto(prisonerId = prisonerAId, prisonId = prisonCode), createPrisonerDto(prisonerId = prisonerBId, prisonId = prisonCode))
    prisonerSearchMockServer.stubGetAllPrisonersForPrison(prisonCode, prisonerSearchResult)
    val responseSpec = callVisitAllocationAdminCountNegativePrisonBalanceEndpoint(prisonCode, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__ADMIN)))

    // Then
    responseSpec.expectStatus().isOk
    val returnResult = getResponse(responseSpec.expectBody())
    assertThat(returnResult.prisonCode).isEqualTo(prisonCode)
    assertThat(returnResult.count).isEqualTo(2)
  }

  @Test
  fun `when prisoner search throws a 500 internal error, then the error is returned to caller`() {
    // Given
    val prisonCode = "HEI"

    // When
    prisonerSearchMockServer.stubGetAllPrisonersForPrison(prisonCode, null, HttpStatus.INTERNAL_SERVER_ERROR)
    val responseSpec = callVisitAllocationAdminCountNegativePrisonBalanceEndpoint(prisonCode, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__ADMIN)))

    // Then
    responseSpec.expectStatus().is5xxServerError
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val prisonCode = "HEI"
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())

    // When
    val responseSpec = callVisitAllocationAdminCountNegativePrisonBalanceEndpoint(prisonCode, webTestClient, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given no auth token
    val prisonCode = "HEI"

    // When
    val responseSpec = webTestClient.get().uri(getAdminCountNegativePrisonBalanceUrl(prisonCode)).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callVisitAllocationAdminCountNegativePrisonBalanceEndpoint(
    prisonCode: String,
    webTestClient: WebTestClient,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callGet(
    webTestClient,
    getAdminCountNegativePrisonBalanceUrl(prisonCode),
    authHttpHeaders,
  )

  private fun getAdminCountNegativePrisonBalanceUrl(prisonCode: String): String = RESET_NEGATIVE_VO_BALANCE_COUNT.replace("{prisonCode}", prisonCode)

  private fun getResponse(returnResult: WebTestClient.BodyContentSpec): PrisonNegativeBalanceCountDto = objectMapper.readValue(
    returnResult.returnResult().responseBody,
    PrisonNegativeBalanceCountDto::class.java,
  )
}
