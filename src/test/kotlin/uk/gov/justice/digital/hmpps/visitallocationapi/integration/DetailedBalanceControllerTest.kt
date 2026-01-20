package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_BALANCE_DETAILED
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerDetailedBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callGet
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate

@DisplayName("Detailed balance Controller tests - $VO_BALANCE_DETAILED")
class DetailedBalanceControllerTest : IntegrationTestBase() {

  companion object {
    const val PRISONER_ID = "AA123456"
  }

  @Test
  fun `when request to get existing prisoner with VO and PVO balance, then return detailed prisoner balance`() {
    // Given
    val prisoner = prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null))

    // prisoner has 2 available VOs, 1 accumulated VO and 1 negative VO
    // prisoner has 0 available PVOs and 1 negative PVO
    visitOrderRepository.save(VisitOrder(type = VisitOrderType.VO, status = VisitOrderStatus.AVAILABLE, prisoner = prisoner))
    visitOrderRepository.save(VisitOrder(type = VisitOrderType.VO, status = VisitOrderStatus.AVAILABLE, prisoner = prisoner))
    visitOrderRepository.save(VisitOrder(type = VisitOrderType.VO, status = VisitOrderStatus.EXPIRED, prisoner = prisoner))
    visitOrderRepository.save(VisitOrder(type = VisitOrderType.VO, status = VisitOrderStatus.ACCUMULATED, prisoner = prisoner))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.VO, status = NegativeVisitOrderStatus.USED, prisoner = prisoner))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.PVO, status = NegativeVisitOrderStatus.USED, prisoner = prisoner))

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceDetailedEndpoint(PRISONER_ID, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk

    val prisonerBalance = getDetailedVoBalanceResponse(responseSpec)

    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.availableVos).isEqualTo(2)
    assertThat(prisonerBalance.accumulatedVos).isEqualTo(1)
    assertThat(prisonerBalance.negativeVos).isEqualTo(1)
    assertThat(prisonerBalance.voBalance).isEqualTo(2)

    assertThat(prisonerBalance.availablePvos).isEqualTo(0)
    assertThat(prisonerBalance.negativePvos).isEqualTo(1)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(-1)
    assertThat(prisonerBalance.lastVoAllocatedDate).isEqualTo(LocalDate.now())
    assertThat(prisonerBalance.nextVoAllocationDate).isEqualTo(LocalDate.now().plusDays(14))
    assertThat(prisonerBalance.lastPvoAllocatedDate).isNull()
    assertThat(prisonerBalance.nextPvoAllocationDate).isEqualTo(prisonerBalance.nextVoAllocationDate)
  }

  @Test
  fun `when request to get existing prisoner with only VO balance, then return detailed prisoner balance`() {
    // Given
    val prisoner = prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null))

    // prisoner has 0 VOs - only 1 expired VO
    // prisoner has 2 available PVOs and 1 negative PVO
    visitOrderRepository.save(VisitOrder(type = VisitOrderType.VO, status = VisitOrderStatus.EXPIRED, prisoner = prisoner))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.VO, status = NegativeVisitOrderStatus.USED, prisoner = prisoner))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.VO, status = NegativeVisitOrderStatus.USED, prisoner = prisoner))

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceDetailedEndpoint(PRISONER_ID, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk

    val prisonerBalance = getDetailedVoBalanceResponse(responseSpec)

    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.availableVos).isEqualTo(0)
    assertThat(prisonerBalance.accumulatedVos).isEqualTo(0)
    assertThat(prisonerBalance.negativeVos).isEqualTo(2)
    assertThat(prisonerBalance.voBalance).isEqualTo(-2)

    assertThat(prisonerBalance.availablePvos).isEqualTo(0)
    assertThat(prisonerBalance.negativePvos).isEqualTo(0)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(0)

    assertThat(prisonerBalance.lastVoAllocatedDate).isEqualTo(LocalDate.now())
    assertThat(prisonerBalance.nextVoAllocationDate).isEqualTo(LocalDate.now().plusDays(14))
    assertThat(prisonerBalance.lastPvoAllocatedDate).isNull()
    assertThat(prisonerBalance.nextPvoAllocationDate).isEqualTo(prisonerBalance.nextVoAllocationDate)
  }

  @Test
  fun `when request to get existing prisoner with only PVO balance, then return detailed prisoner balance`() {
    // Given
    val prisoner = prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = LocalDate.now().minusDays(1)))

    // prisoner has 0 VOs - only 1 expired VO
    // prisoner has 2 available PVOs and 1 negative PVO
    visitOrderRepository.save(VisitOrder(type = VisitOrderType.VO, status = VisitOrderStatus.EXPIRED, prisoner = prisoner))
    visitOrderRepository.save(VisitOrder(type = VisitOrderType.PVO, status = VisitOrderStatus.AVAILABLE, prisoner = prisoner))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.VO, status = NegativeVisitOrderStatus.USED, prisoner = prisoner))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.VO, status = NegativeVisitOrderStatus.USED, prisoner = prisoner))
    negativeVisitOrderRepository.save(NegativeVisitOrder(type = VisitOrderType.PVO, status = NegativeVisitOrderStatus.USED, prisoner = prisoner))

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceDetailedEndpoint(PRISONER_ID, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk

    val prisonerBalance = getDetailedVoBalanceResponse(responseSpec)

    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.availableVos).isEqualTo(0)
    assertThat(prisonerBalance.accumulatedVos).isEqualTo(0)
    assertThat(prisonerBalance.negativeVos).isEqualTo(2)
    assertThat(prisonerBalance.voBalance).isEqualTo(-2)

    assertThat(prisonerBalance.availablePvos).isEqualTo(1)
    assertThat(prisonerBalance.negativePvos).isEqualTo(1)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(0)

    assertThat(prisonerBalance.lastVoAllocatedDate).isEqualTo(LocalDate.now())
    assertThat(prisonerBalance.nextVoAllocationDate).isEqualTo(LocalDate.now().plusDays(14))
    assertThat(prisonerBalance.lastPvoAllocatedDate).isEqualTo(LocalDate.now().minusDays(1))
    assertThat(prisonerBalance.nextPvoAllocationDate).isEqualTo(LocalDate.now().plusDays(27))
  }

  @Test
  fun `when request to get an unknown prisoner, then status 404 NOT_FOUND is returned`() {
    // Given
    // When
    val responseSpec = callVisitAllocationPrisonerBalanceDetailedEndpoint(PRISONER_ID, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceDetailedEndpoint(PRISONER_ID, webTestClient, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given no auth token

    // When
    val responseSpec = webTestClient.get().uri(getPrisonerDetailedBalanceUrl(PRISONER_ID)).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callVisitAllocationPrisonerBalanceDetailedEndpoint(
    prisonerId: String,
    webTestClient: WebTestClient,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callGet(
    webTestClient,
    getPrisonerDetailedBalanceUrl(prisonerId),
    authHttpHeaders,
  )

  private fun getPrisonerDetailedBalanceUrl(prisonerId: String): String = VO_BALANCE_DETAILED.replace("{prisonerId}", prisonerId)

  private fun getDetailedVoBalanceResponse(responseSpec: ResponseSpec): PrisonerDetailedBalanceDto = TestObjectMapper.mapper.readValue(responseSpec.expectBody().returnResult().responseBody, PrisonerDetailedBalanceDto::class.java)
}
