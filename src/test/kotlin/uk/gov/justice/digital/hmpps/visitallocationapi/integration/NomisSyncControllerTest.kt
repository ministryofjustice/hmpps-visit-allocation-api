package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_MIGRATION
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callPost
import java.time.LocalDate

@DisplayName("NomisSyncController tests")
class NomisSyncControllerTest : IntegrationTestBase() {

  @Test
  fun `migrate prisoner - when visit prisoner allocation migration endpoint is called, then prisoner information is successfully migrated to DPS service`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", 5, 2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf("VISIT_ALLOCATION_MIGRATION")))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `migrate prisoner - when request body validation fails then 400 bad request is returned`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("", 5, 2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf("VISIT_ALLOCATION_MIGRATION")))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `migrate prisoner - access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", 5, 2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `migrate prisoner - unauthorised when no token`() {
    // Given no auth token

    // When
    val responseSpec = webTestClient.post().uri(VO_MIGRATION).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  fun callVisitAllocationMigrationEndpoint(
    webTestClient: WebTestClient,
    dto: VisitAllocationPrisonerMigrationDto? = null,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callPost(
    dto,
    webTestClient,
    VO_MIGRATION,
    authHttpHeaders,
  )
}
