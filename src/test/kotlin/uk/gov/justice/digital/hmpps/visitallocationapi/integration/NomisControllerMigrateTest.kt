package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__NOMIS_API
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_PRISONER_MIGRATION
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callPost
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate

@DisplayName("NomisController migration tests - $VO_PRISONER_MIGRATION")
class NomisControllerMigrateTest : IntegrationTestBase() {

  @Test
  fun `when visit prisoner allocation migration endpoint is called with positive balance, then prisoner information is successfully migrated to DPS service`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", 5, 2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk

    verify(negativeVisitOrderRepository, times(0)).saveAll<NegativeVisitOrder>(any())
    verify(prisonerDetailsRepository, times(1)).insertNewPrisonerDetails(any(), any(), any())

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
  fun `when visit prisoner allocation migration endpoint is called with negative balance, then prisoner information is successfully migrated to DPS service`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", -5, -2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk

    verify(visitOrderRepository, times(0)).saveAll<VisitOrder>(any())
    verify(prisonerDetailsRepository, times(1)).insertNewPrisonerDetails(any(), any(), any())

    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.size).isEqualTo(7)
    assertThat(negativeVisitOrders.filter { it.type == VisitOrderType.VO }.size).isEqualTo(5)
    assertThat(negativeVisitOrders.filter { it.type == VisitOrderType.PVO }.size).isEqualTo(2)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails.first().prisonerId).isEqualTo(prisonerMigrationDto.prisonerId)
    assertThat(prisonerDetails.first().lastVoAllocatedDate).isEqualTo(prisonerMigrationDto.lastVoAllocationDate)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MIGRATION)
  }

  @Test
  fun `when visit prisoner allocation migration endpoint is called with a mixed balance, then prisoner information is successfully migrated to DPS service`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", 5, -2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk

    verify(prisonerDetailsRepository, times(1)).insertNewPrisonerDetails(any(), any(), any())

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(5)
    assertThat(visitOrders.filter { it.type == VisitOrderType.VO }.size).isEqualTo(5)
    assertThat(visitOrders.filter { it.type == VisitOrderType.PVO }.size).isEqualTo(0)

    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.size).isEqualTo(2)
    assertThat(negativeVisitOrders.filter { it.type == VisitOrderType.VO }.size).isEqualTo(0)
    assertThat(negativeVisitOrders.filter { it.type == VisitOrderType.PVO }.size).isEqualTo(2)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails.first().prisonerId).isEqualTo(prisonerMigrationDto.prisonerId)
    assertThat(prisonerDetails.first().lastVoAllocatedDate).isEqualTo(prisonerMigrationDto.lastVoAllocationDate)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MIGRATION)
  }

  @Test
  fun `when visit prisoner allocation migration endpoint is called with missing lastAllocatedDate, then default value of TODAY - 28 DAYS is used`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", 5, 2, null)

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk

    verify(negativeVisitOrderRepository, times(0)).saveAll<NegativeVisitOrder>(any())
    verify(prisonerDetailsRepository, times(1)).insertNewPrisonerDetails(any(), any(), any())

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(7)
    assertThat(visitOrders.filter { it.type == VisitOrderType.VO }.size).isEqualTo(5)
    assertThat(visitOrders.filter { it.type == VisitOrderType.PVO }.size).isEqualTo(2)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails.first().prisonerId).isEqualTo(prisonerMigrationDto.prisonerId)
    assertThat(prisonerDetails.first().lastVoAllocatedDate).isEqualTo(LocalDate.now().minusDays(28))

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MIGRATION)
  }

  @Test
  fun `when visit prisoner allocation migration endpoint is called with an existing prisoner, then prisoner information is reset and then successfully migrated to DPS service`() {
    // Given
    val prisoner = PrisonerDetails(prisonerId = "AA123456", lastVoAllocatedDate = LocalDate.now().minusDays(1), null)
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", 5, 2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk

    verify(negativeVisitOrderRepository, times(0)).saveAll<NegativeVisitOrder>(any())
    verify(prisonerDetailsRepository, times(1)).insertNewPrisonerDetails(any(), any(), any())

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
  fun `when request body validation fails then 400 bad request is returned`() {
    // Given
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("", 5, 2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())
    val prisonerMigrationDto = VisitAllocationPrisonerMigrationDto("AA123456", 5, 2, LocalDate.now().minusDays(1))

    // When
    val responseSpec = callVisitAllocationMigrationEndpoint(webTestClient, prisonerMigrationDto, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given no auth token

    // When
    val responseSpec = webTestClient.post().uri(VO_PRISONER_MIGRATION).exchange()

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
}
