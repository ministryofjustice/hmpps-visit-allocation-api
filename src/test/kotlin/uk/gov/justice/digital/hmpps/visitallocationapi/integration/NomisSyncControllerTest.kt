package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_PRISONER_MIGRATION
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_PRISONER_SYNC
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callPost
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.ChangeLogRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import java.time.LocalDate

@DisplayName("NomisSyncController tests")
class NomisSyncControllerTest : IntegrationTestBase() {

  @MockitoSpyBean
  private lateinit var visitOrderRepository: VisitOrderRepository

  @MockitoSpyBean
  private lateinit var negativeVisitOrderRepository: NegativeVisitOrderRepository

  @MockitoSpyBean
  private lateinit var prisonerDetailsRepository: PrisonerDetailsRepository

  @MockitoSpyBean
  private lateinit var changeLogRepository: ChangeLogRepository

  @Test
  fun `migrate prisoner - when visit prisoner allocation migration endpoint is called with positive balance, then prisoner information is successfully migrated to DPS service`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", 5, 2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk

    verify(visitOrderRepository, times(2)).saveAll<VisitOrder>(any())
    verify(negativeVisitOrderRepository, times(0)).saveAll<NegativeVisitOrder>(any())
    verify(prisonerDetailsRepository, times(1)).save<PrisonerDetails>(any())
    verify(changeLogRepository, times(1)).save<ChangeLog>(any())

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(7)
    assertThat(visitOrders.filter { it.type == VisitOrderType.VO }.size).isEqualTo(5)
    assertThat(visitOrders.filter { it.type == VisitOrderType.PVO }.size).isEqualTo(2)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails.first().prisonerId).isEqualTo(prisonerMigrationDto.prisonerId)
    assertThat(prisonerDetails.first().lastVoAllocatedDate).isEqualTo(prisonerMigrationDto.lastVoAllocationDate)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MIGRATION)
  }

  @Test
  fun `migrate prisoner - when visit prisoner allocation migration endpoint is called with negative balance, then prisoner information is successfully migrated to DPS service`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", -5, -2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk

    verify(visitOrderRepository, times(0)).saveAll<VisitOrder>(any())
    verify(negativeVisitOrderRepository, times(2)).saveAll<NegativeVisitOrder>(any())
    verify(prisonerDetailsRepository, times(1)).save<PrisonerDetails>(any())
    verify(changeLogRepository, times(1)).save<ChangeLog>(any())

    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.size).isEqualTo(7)
    assertThat(negativeVisitOrders.filter { it.type == NegativeVisitOrderType.NEGATIVE_VO }.size).isEqualTo(5)
    assertThat(negativeVisitOrders.filter { it.type == NegativeVisitOrderType.NEGATIVE_PVO }.size).isEqualTo(2)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails.first().prisonerId).isEqualTo(prisonerMigrationDto.prisonerId)
    assertThat(prisonerDetails.first().lastVoAllocatedDate).isEqualTo(prisonerMigrationDto.lastVoAllocationDate)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MIGRATION)
  }

  @Test
  fun `migrate prisoner - when visit prisoner allocation migration endpoint is called with a mixed balance, then prisoner information is successfully migrated to DPS service`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", 5, -2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk

    verify(visitOrderRepository, times(1)).saveAll<VisitOrder>(any())
    verify(negativeVisitOrderRepository, times(1)).saveAll<NegativeVisitOrder>(any())
    verify(prisonerDetailsRepository, times(1)).save<PrisonerDetails>(any())
    verify(changeLogRepository, times(1)).save<ChangeLog>(any())

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(5)
    assertThat(visitOrders.filter { it.type == VisitOrderType.VO }.size).isEqualTo(5)
    assertThat(visitOrders.filter { it.type == VisitOrderType.PVO }.size).isEqualTo(0)

    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.size).isEqualTo(2)
    assertThat(negativeVisitOrders.filter { it.type == NegativeVisitOrderType.NEGATIVE_VO }.size).isEqualTo(0)
    assertThat(negativeVisitOrders.filter { it.type == NegativeVisitOrderType.NEGATIVE_PVO }.size).isEqualTo(2)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails.first().prisonerId).isEqualTo(prisonerMigrationDto.prisonerId)
    assertThat(prisonerDetails.first().lastVoAllocatedDate).isEqualTo(prisonerMigrationDto.lastVoAllocationDate)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MIGRATION)
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
    val prisonerSyncDto = VisitAllocationPrisonerSyncDto("AA123456", 5, 1, 2, 0, LocalDate.now().minusDays(1), AdjustmentReasonCode.VO_ISSUE, ChangeLogSource.SYSTEM, "issued vo")

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `sync prisoner - when request body validation fails then 400 bad request is returned`() {
    // Given
    val prisonerSyncDto = VisitAllocationPrisonerSyncDto("", 5, 1, 2, 0, LocalDate.now().minusDays(1), AdjustmentReasonCode.VO_ISSUE, ChangeLogSource.SYSTEM, "issued vo")

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `sync prisoner - access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())
    val prisonerSyncDto = VisitAllocationPrisonerSyncDto("AA123456", 5, 1, 2, 0, LocalDate.now().minusDays(1), AdjustmentReasonCode.VO_ISSUE, ChangeLogSource.SYSTEM, "issued vo")

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
