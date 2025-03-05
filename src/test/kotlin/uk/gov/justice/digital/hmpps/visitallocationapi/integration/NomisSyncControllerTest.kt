package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_PRISONER_MIGRATION
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_PRISONER_SYNC
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeSource
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callPost
import java.time.LocalDate

@DisplayName("NomisSyncController tests")
class NomisSyncControllerTest : IntegrationTestBase() {

  @Test
  fun `migrate prisoner - when visit prisoner allocation migration endpoint is called, then prisoner information is successfully migrated to DPS service`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", 5, 2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `migrate prisoner - when request body validation fails then 400 bad request is returned`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("", 5, 2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

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
    val responseSpec = webTestClient.post().uri(VO_PRISONER_MIGRATION).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `sync prisoner - when visit prisoner allocation sync endpoint is called, then prisoner information is successfully synced to DPS service`() {
    // Given
    val prisonerSyncDto = VisitAllocationPrisonerSyncDto("AA123456", 5, 1, 2, 0, LocalDate.now().minusDays(1), AdjustmentReasonCode.VO_ISSUE, ChangeSource.SYSTEM)

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `sync prisoner - when request body validation fails then 400 bad request is returned`() {
    // Given
    val prisonerSyncDto = VisitAllocationPrisonerSyncDto("", 5, 1, 2, 0, LocalDate.now().minusDays(1), AdjustmentReasonCode.VO_ISSUE, ChangeSource.SYSTEM)

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `sync prisoner - access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())
    val prisonerSyncDto = VisitAllocationPrisonerSyncDto("AA123456", 5, 1, 2, 0, LocalDate.now().minusDays(1), AdjustmentReasonCode.VO_ISSUE, ChangeSource.SYSTEM)

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `sync prisoner - unauthorised when no token`() {
    // Given no auth token

    // When
    val responseSpec = webTestClient.post().uri(VO_PRISONER_SYNC).exchange()

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
    VO_PRISONER_MIGRATION,
    authHttpHeaders,
  )

  fun callVisitAllocationSyncEndpoint(
    webTestClient: WebTestClient,
    dto: VisitAllocationPrisonerSyncDto? = null,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callPost(
    dto,
    webTestClient,
    VO_PRISONER_SYNC,
    authHttpHeaders,
  )
}
