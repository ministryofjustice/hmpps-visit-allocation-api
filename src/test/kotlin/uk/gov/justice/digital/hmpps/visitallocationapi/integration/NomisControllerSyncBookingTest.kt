package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__NOMIS_API
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_PRISONER_BOOKING_MOVE_SYNC
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_PRISONER_SYNC
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncBookingDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callPost
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import java.time.LocalDate

@DisplayName("NomisController sync booking tests - $VO_PRISONER_BOOKING_MOVE_SYNC")
class NomisControllerSyncBookingTest : IntegrationTestBase() {

  companion object {
    const val FIRST_PRISONER_ID = "AA123456"
    const val SECOND_PRISONER_ID = "BB123456"
  }

  /**
   * Scenario 1: Existing Prisoner who has a positive balance which is in sync, has an increase to their balance. DPS syncs successfully.
   */
  @Test
  fun `when a booking sync request comes in, then both prisoners who are effected are successfully processed and synced`() {
    // Given
    entityHelper.createAndSaveVisitOrders(prisonerId = FIRST_PRISONER_ID, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = FIRST_PRISONER_ID, VisitOrderType.PVO, 1)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = FIRST_PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    entityHelper.createAndSaveVisitOrders(prisonerId = SECOND_PRISONER_ID, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = SECOND_PRISONER_ID, VisitOrderType.PVO, 1)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = SECOND_PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val syncDto = VisitAllocationPrisonerSyncBookingDto(
      firstPrisonerId = FIRST_PRISONER_ID,
      firstPrisonerVoBalance = 4,
      firstPrisonerPvoBalance = 2,
      secondPrisonerId = SECOND_PRISONER_ID,
      secondPrisonerVoBalance = 2,
      secondPrisonerPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncBookingEndpoint(webTestClient, syncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertBookingSyncResults(FIRST_PRISONER_ID, 4, 2)
    assertBookingSyncResults(SECOND_PRISONER_ID, 2, 1)
  }

  @Test
  fun `when request body validation fails then 400 bad request is returned`() {
    // Given
    val syncDto = VisitAllocationPrisonerSyncBookingDto(
      firstPrisonerId = "",
      firstPrisonerVoBalance = 4,
      firstPrisonerPvoBalance = 2,
      secondPrisonerId = SECOND_PRISONER_ID,
      secondPrisonerVoBalance = 2,
      secondPrisonerPvoBalance = 1,
    )
    // When
    val responseSpec = callVisitAllocationSyncBookingEndpoint(webTestClient, syncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())
    val syncDto = VisitAllocationPrisonerSyncBookingDto(
      firstPrisonerId = FIRST_PRISONER_ID,
      firstPrisonerVoBalance = 4,
      firstPrisonerPvoBalance = 2,
      secondPrisonerId = SECOND_PRISONER_ID,
      secondPrisonerVoBalance = 2,
      secondPrisonerPvoBalance = 1,
    )
    // When
    val responseSpec = callVisitAllocationSyncBookingEndpoint(webTestClient, syncDto, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given no auth token

    // When
    val responseSpec = webTestClient.post().uri(VO_PRISONER_SYNC).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callVisitAllocationSyncBookingEndpoint(
    webTestClient: WebTestClient,
    dto: VisitAllocationPrisonerSyncBookingDto? = null,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callPost(
    dto,
    webTestClient,
    VO_PRISONER_BOOKING_MOVE_SYNC,
    authHttpHeaders,
  )

  private fun assertBookingSyncResults(prisonerId: String, expectedVoCount: Int, expectedPvoCount: Int) {
    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.prisonerId == prisonerId && it.status == VisitOrderStatus.AVAILABLE && it.type == VisitOrderType.VO }.size).isEqualTo(expectedVoCount)
    assertThat(visitOrders.filter { it.prisonerId == prisonerId && it.status == VisitOrderStatus.AVAILABLE && it.type == VisitOrderType.PVO }.size).isEqualTo(expectedPvoCount)

    val changeLogs = changeLogRepository.findByPrisonerId(prisonerId)
    assertThat(changeLogs.size).isEqualTo(1)
    assertThat(changeLogs.first().prisonerId).isEqualTo(prisonerId)
    assertThat(changeLogs.first().changeType).isEqualTo(ChangeLogType.SYNC_BOOKING)
  }
}
