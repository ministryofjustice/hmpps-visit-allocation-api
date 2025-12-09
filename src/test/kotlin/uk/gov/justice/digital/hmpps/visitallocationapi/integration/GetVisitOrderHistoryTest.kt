package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.GET_VISIT_ORDER_HISTORY
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.VisitOrderHistoryDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryAttributeType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callGet
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistory
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistoryAttributes
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("Visit order history tests - $GET_VISIT_ORDER_HISTORY")
class GetVisitOrderHistoryTest : IntegrationTestBase() {

  companion object {
    const val PRISONER_ID = "AA123456"
  }

  @Test
  fun `when request to get prisoner with visit order history then return all visit order history on or after fromDate`() {
    // Given
    val fromDate = LocalDate.now().minusDays(30)
    val prisoner = prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null))

    // this record will not be returned as it is before fromDate
    val visitOrderHistory1 = VisitOrderHistory(prisoner = prisoner, type = VisitOrderHistoryType.MIGRATION, voBalance = 1, pvoBalance = 3, userName = "SYSTEM", createdTimestamp = LocalDateTime.now().minusDays(31))

    val visitOrderHistory2 = VisitOrderHistory(prisoner = prisoner, type = VisitOrderHistoryType.VO_ALLOCATION, voBalance = 2, pvoBalance = 3, userName = "SYSTEM", createdTimestamp = LocalDateTime.now().minusDays(29))
    var visitOrderHistory3 = VisitOrderHistory(prisoner = prisoner, type = VisitOrderHistoryType.PVO_EXPIRATION, voBalance = 2, pvoBalance = 2, comment = "testing", userName = "SYSTEM", createdTimestamp = LocalDateTime.now().minusDays(29))
    var visitOrderHistory4 = VisitOrderHistory(prisoner = prisoner, type = VisitOrderHistoryType.VO_AND_PVO_EXPIRATION, voBalance = 2, pvoBalance = 2, userName = "SYSTEM", createdTimestamp = LocalDateTime.now().minusDays(29))

    visitOrderHistoryRepository.save(visitOrderHistory1)
    visitOrderHistoryRepository.save(visitOrderHistory2)
    visitOrderHistory3 = visitOrderHistoryRepository.save(visitOrderHistory3)
    visitOrderHistory4 = visitOrderHistoryRepository.save(visitOrderHistory4)

    val visitOrderHistory3Attribute1 = VisitOrderHistoryAttributes(visitOrderHistory = visitOrderHistory3, attributeType = VisitOrderHistoryAttributeType.VISIT_REFERENCE, attributeValue = "aa-bb-cc")
    val visitOrderHistory3Attribute2 = VisitOrderHistoryAttributes(visitOrderHistory = visitOrderHistory3, attributeType = VisitOrderHistoryAttributeType.INCENTIVE_LEVEL, attributeValue = "ENH")
    val visitOrderHistory4Attribute = VisitOrderHistoryAttributes(visitOrderHistory = visitOrderHistory4, attributeType = VisitOrderHistoryAttributeType.INCENTIVE_LEVEL, attributeValue = "STD")

    visitOrderHistory3.visitOrderHistoryAttributes.add(visitOrderHistory3Attribute1)
    visitOrderHistory3.visitOrderHistoryAttributes.add(visitOrderHistory3Attribute2)
    visitOrderHistory4.visitOrderHistoryAttributes.add(visitOrderHistory4Attribute)
    visitOrderHistoryRepository.save(visitOrderHistory3)
    visitOrderHistoryRepository.save(visitOrderHistory4)

    // When
    val responseSpec = callVisitHistoryEndpoint(PRISONER_ID, fromDate, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk

    val visitOrderHistoryList = getVisitOrderHistoryUrlResponse(responseSpec)

    assertThat(visitOrderHistoryList.size).isEqualTo(3)
    assertVisitOrderHistory(visitOrderHistoryList[0], visitOrderHistory2, emptyList())
    assertVisitOrderHistory(visitOrderHistoryList[1], visitOrderHistory3, listOf(visitOrderHistory3Attribute1, visitOrderHistory3Attribute2))
    assertVisitOrderHistory(visitOrderHistoryList[2], visitOrderHistory4, listOf(visitOrderHistory4Attribute))
  }

  @Test
  fun `when request to get prisoner with visit order history and none exist for prisoner after fromDate an empty list is returned`() {
    // Given
    val fromDate = LocalDate.now().minusDays(30)
    val prisoner = prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null))

    // this record will not be returned as it is before fromDate
    val visitOrderHistory1 = VisitOrderHistory(prisoner = prisoner, type = VisitOrderHistoryType.MIGRATION, voBalance = 1, pvoBalance = 3, userName = "SYSTEM", createdTimestamp = LocalDateTime.now().minusDays(31))
    visitOrderHistoryRepository.save(visitOrderHistory1)

    // When
    val responseSpec = callVisitHistoryEndpoint(PRISONER_ID, fromDate, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk

    val visitOrderHistoryList = getVisitOrderHistoryUrlResponse(responseSpec)
    assertThat(visitOrderHistoryList.size).isEqualTo(0)
  }

  @Test
  fun `when request to get an unknown prisoner, then empty list is returned`() {
    // Given
    // When
    val responseSpec = callVisitHistoryEndpoint(PRISONER_ID, LocalDate.now(), webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk

    val visitOrderHistoryList = getVisitOrderHistoryUrlResponse(responseSpec)
    assertThat(visitOrderHistoryList.size).isEqualTo(0)
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())

    // When
    val responseSpec = callVisitHistoryEndpoint(PRISONER_ID, LocalDate.now(), webTestClient, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given no auth token

    // When
    val fromDate = LocalDate.now()
    val responseSpec = webTestClient.get().uri(getVisitOrderHistoryUrl(PRISONER_ID, fromDate)).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callVisitHistoryEndpoint(
    prisonerId: String,
    fromDate: LocalDate,
    webTestClient: WebTestClient,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callGet(
    webTestClient,
    getVisitOrderHistoryUrl(prisonerId, fromDate),
    authHttpHeaders,
  )

  private fun getVisitOrderHistoryUrl(prisonerId: String, fromDate: LocalDate): String = "$GET_VISIT_ORDER_HISTORY?fromDate=$fromDate".replace("{prisonerId}", prisonerId)

  private fun assertVisitOrderHistory(
    visitOrderHistoryDto: VisitOrderHistoryDto,
    visitOrderHistory: VisitOrderHistory,
    visitOrderHistoryAttributes: List<VisitOrderHistoryAttributes>,
  ) {
    assertThat(visitOrderHistoryDto.visitOrderHistoryType).isEqualTo(visitOrderHistory.type)
    assertThat(visitOrderHistoryDto.voBalance).isEqualTo(visitOrderHistory.voBalance)
    assertThat(visitOrderHistoryDto.pvoBalance).isEqualTo(visitOrderHistory.pvoBalance)
    assertThat(visitOrderHistoryDto.comment).isEqualTo(visitOrderHistory.comment)
    assertThat(visitOrderHistoryDto.userName).isEqualTo(visitOrderHistory.userName)
    assertVisitOrderHistoryAttributes(visitOrderHistoryDto, visitOrderHistoryAttributes)
  }

  private fun assertVisitOrderHistoryAttributes(
    visitOrderHistoryDto: VisitOrderHistoryDto,
    visitOrderHistoryAttributes: List<VisitOrderHistoryAttributes>,
  ) {
    assertThat(visitOrderHistoryDto.attributes.size).isEqualTo(visitOrderHistoryAttributes.size)
    visitOrderHistoryAttributes.forEachIndexed { i, visitOrderHistoryAttribute ->
      assertThat(visitOrderHistoryDto.attributes.toList()[i].first).isEqualTo(visitOrderHistoryAttribute.attributeType)
      assertThat(visitOrderHistoryDto.attributes.toList()[i].second).isEqualTo(visitOrderHistoryAttribute.attributeValue)
    }
  }

  private fun getVisitOrderHistoryUrlResponse(responseSpec: ResponseSpec): List<VisitOrderHistoryDto> = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<VisitOrderHistoryDto>::class.java).toList()
}
