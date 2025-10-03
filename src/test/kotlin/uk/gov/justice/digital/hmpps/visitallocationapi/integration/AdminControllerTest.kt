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
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callPost
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate

@DisplayName("Admin Controller tests - $RESET_NEGATIVE_VO_BALANCE")
class AdminControllerTest : IntegrationTestBase() {

  @Test
  fun `when admin request to reset negative balances for prisoners comes in, all negative balances are reset`() {
    // Given
    val prisonCode = "HEI"
    val prisonerId = "AA123456"
    val prisoner = prisonerDetailsRepository.save(PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.VO, status = NegativeVisitOrderStatus.USED, prisoner = prisoner))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.PVO, status = NegativeVisitOrderStatus.USED, prisoner = prisoner))

    // When
    val prisonerSearchResult = listOf(createPrisonerDto(prisonerId = prisonerId, prisonId = prisonCode))
    prisonerSearchMockServer.stubGetAllPrisonersForPrison(prisonCode, prisonerSearchResult)
    val responseSpec = callVisitAllocationAdminResetNegativePrisonBalanceEndpoint(prisonCode, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__ADMIN)))

    // Then
    responseSpec.expectStatus().isOk

    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.size).isEqualTo(2)
    assertThat(negativeVisitOrders.filter { it.status == NegativeVisitOrderStatus.USED }.size).isEqualTo(0)
    assertThat(negativeVisitOrders.filter { it.status == NegativeVisitOrderStatus.REPAID }.size).isEqualTo(2)
  }

  @Test
  fun `when admin request to reset negative balances for prisoners comes in, and no negative balances exist, nothing changes`() {
    // Given
    val prisonCode = "HEI"
    val prisonerId = "AA123456"
    val prisoner = prisonerDetailsRepository.save(PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null))
    visitOrderRepository.save(VisitOrder(type = VisitOrderType.VO, status = VisitOrderStatus.AVAILABLE, prisoner = prisoner))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.VO, status = NegativeVisitOrderStatus.REPAID, prisoner = prisoner))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.PVO, status = NegativeVisitOrderStatus.REPAID, prisoner = prisoner))

    // When
    val prisonerSearchResult = listOf(createPrisonerDto(prisonerId = prisonerId, prisonId = prisonCode))
    prisonerSearchMockServer.stubGetAllPrisonersForPrison(prisonCode, prisonerSearchResult)
    val responseSpec = callVisitAllocationAdminResetNegativePrisonBalanceEndpoint(prisonCode, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__ADMIN)))

    // Then
    responseSpec.expectStatus().isOk

    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.size).isEqualTo(2)
    assertThat(negativeVisitOrders.filter { it.status == NegativeVisitOrderStatus.USED }.size).isEqualTo(0)
    assertThat(negativeVisitOrders.filter { it.status == NegativeVisitOrderStatus.REPAID }.size).isEqualTo(2)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(1)
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(1)
  }

  @Test
  fun `when prisoner search throws a 500 internal error, then the error is returned to caller`() {
    // Given
    val prisonCode = "HEI"

    // When
    prisonerSearchMockServer.stubGetAllPrisonersForPrison(prisonCode, null, HttpStatus.INTERNAL_SERVER_ERROR)
    val responseSpec = callVisitAllocationAdminResetNegativePrisonBalanceEndpoint(prisonCode, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__ADMIN)))

    // Then
    responseSpec.expectStatus().is5xxServerError
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val prisonCode = "HEI"
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())

    // When
    val responseSpec = callVisitAllocationAdminResetNegativePrisonBalanceEndpoint(prisonCode, webTestClient, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given no auth token
    val prisonCode = "HEI"

    // When
    val responseSpec = webTestClient.get().uri(getAdminResetNegativePrisonBalanceUrl(prisonCode)).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callVisitAllocationAdminResetNegativePrisonBalanceEndpoint(
    prisonCode: String,
    webTestClient: WebTestClient,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callPost(
    null,
    webTestClient,
    getAdminResetNegativePrisonBalanceUrl(prisonCode),
    authHttpHeaders,
  )

  private fun getAdminResetNegativePrisonBalanceUrl(prisonCode: String): String = RESET_NEGATIVE_VO_BALANCE.replace("{prisonCode}", prisonCode)
}
