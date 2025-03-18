package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_PRISONER_SYNC
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callPost
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate

@DisplayName("NomisController sync tests - $VO_PRISONER_SYNC")
class NomisControllerSyncTest : IntegrationTestBase() {

  companion object {
    const val PRISONER_ID = "AA123456"
  }

  @Test
  fun `when an existing prisoner with a positive balance increases, then DPS service successfully syncs`() {
    // Given
    createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 5)
    createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 2)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 5,
      changeToVoBalance = 1,
      oldPvoBalance = 2,
      changeToPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 6, expectedPvoCount = 3, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verifyNoInteractions(telemetryClientService)
  }

  @Test
  fun `when an existing prisoner with a negative balance decreases, then DPS service successfully syncs`() {
    // Given
    createAndSaveNegativeVisitOrders(prisonerId = PRISONER_ID, NegativeVisitOrderType.NEGATIVE_VO, 5)
    createAndSaveNegativeVisitOrders(prisonerId = PRISONER_ID, NegativeVisitOrderType.NEGATIVE_PVO, 2)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = -5,
      changeToVoBalance = -1,
      oldPvoBalance = -2,
      changeToPvoBalance = -1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 0, expectedPvoCount = 0, expectedNegativeVoCount = 6, expectedNegativePvoCount = 3)
    verifyNoInteractions(telemetryClientService)
  }

  @Test
  fun `when an existing prisoner with a positive balance decreases below zero, then DPS service successfully syncs`() {
    // Given
    createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 2)
    createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 1)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 2,
      changeToVoBalance = -3,
      oldPvoBalance = 1,
      changeToPvoBalance = -2,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 0, expectedPvoCount = 0, expectedNegativeVoCount = 1, expectedNegativePvoCount = 1)
    verifyNoInteractions(telemetryClientService)
  }

  @Test
  fun `when an existing prisoner with a negative balance increases above zero, then DPS service successfully syncs`() {
    // Given
    createAndSaveNegativeVisitOrders(prisonerId = PRISONER_ID, NegativeVisitOrderType.NEGATIVE_VO, 2)
    createAndSaveNegativeVisitOrders(prisonerId = PRISONER_ID, NegativeVisitOrderType.NEGATIVE_PVO, 1)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = -2,
      changeToVoBalance = 3,
      oldPvoBalance = -1,
      changeToPvoBalance = 2,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 1, expectedPvoCount = 1, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verifyNoInteractions(telemetryClientService)
  }

  @Test
  fun `when an existing prisoner with an out of sync (DPS higher) positive balance increases, then DPS service successfully syncs`() {
    // Given
    createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 6)
    createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 3)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 5,
      changeToVoBalance = 1,
      oldPvoBalance = 2,
      changeToPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 6, expectedPvoCount = 3, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verify(telemetryClientService, times(1)).trackEvent(any(), any())
  }

  @Test
  fun `when an existing prisoner with an out of sync (NOMIS higher) positive balance increases, then DPS service successfully syncs`() {
    // Given
    createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 4)
    createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 1)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 5,
      changeToVoBalance = 1,
      oldPvoBalance = 2,
      changeToPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 6, expectedPvoCount = 3, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verify(telemetryClientService, times(1)).trackEvent(any(), any())
  }

  @Test
  fun `when request body validation fails then 400 bad request is returned`() {
    // Given
    val prisonerSyncDto = VisitAllocationPrisonerSyncDto("", 5, 1, 2, 0, LocalDate.now().minusDays(1), AdjustmentReasonCode.VO_ISSUE, ChangeLogSource.SYSTEM, "issued vo")

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf("ROLE_VISIT_ALLOCATION_API__NOMIS_API")))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())
    val prisonerSyncDto = VisitAllocationPrisonerSyncDto("AA123456", 5, 1, 2, 0, LocalDate.now().minusDays(1), AdjustmentReasonCode.VO_ISSUE, ChangeLogSource.SYSTEM, "issued vo")

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, incorrectAuthHeaders)

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

  private fun callVisitAllocationSyncEndpoint(
    webTestClient: WebTestClient,
    dto: VisitAllocationPrisonerSyncDto? = null,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callPost(
    dto,
    webTestClient,
    VO_PRISONER_SYNC,
    authHttpHeaders,
  )

  private fun createSyncRequest(
    prisonerId: String,
    oldVoBalance: Int,
    changeToVoBalance: Int? = null,
    oldPvoBalance: Int,
    changeToPvoBalance: Int? = null,
    createdDate: LocalDate = LocalDate.now(),
    adjustmentReasonCode: AdjustmentReasonCode = AdjustmentReasonCode.IEP,
    changeLogSource: ChangeLogSource = ChangeLogSource.SYSTEM,
    comment: String? = null,
  ): VisitAllocationPrisonerSyncDto = VisitAllocationPrisonerSyncDto(
    prisonerId,
    oldVoBalance,
    changeToVoBalance,
    oldPvoBalance,
    changeToPvoBalance,
    createdDate,
    adjustmentReasonCode,
    changeLogSource,
    comment,
  )

  private fun createAndSaveVisitOrders(prisonerId: String, visitOrderType: VisitOrderType, amountToCreate: Int) {
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(amountToCreate) {
      visitOrders.add(
        VisitOrder(
          prisonerId = prisonerId,
          type = visitOrderType,
          status = VisitOrderStatus.AVAILABLE,
        ),
      )
    }
    visitOrderRepository.saveAll(visitOrders)
  }

  private fun createAndSaveNegativeVisitOrders(prisonerId: String, negativeVoType: NegativeVisitOrderType, amountToCreate: Int) {
    val negativeVisitOrders = mutableListOf<NegativeVisitOrder>()
    repeat(amountToCreate) {
      negativeVisitOrders.add(
        NegativeVisitOrder(
          prisonerId = prisonerId,
          type = negativeVoType,
          status = NegativeVisitOrderStatus.USED,
        ),
      )
    }
    negativeVisitOrderRepository.saveAll(negativeVisitOrders)
  }

  private fun assertSyncResults(prisonerSyncDto: VisitAllocationPrisonerSyncDto, expectedVoCount: Int, expectedPvoCount: Int, expectedNegativeVoCount: Int, expectedNegativePvoCount: Int) {
    val visitOrders = visitOrderRepository.findAll()

    val expectedVisitOrderTotal = expectedVoCount + expectedPvoCount
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(expectedVisitOrderTotal)
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE && it.type == VisitOrderType.VO }.size).isEqualTo(expectedVoCount)
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE && it.type == VisitOrderType.PVO }.size).isEqualTo(expectedPvoCount)

    val prisonerDetails = prisonerDetailsRepository.findByPrisonerId(prisonerSyncDto.prisonerId)!!
    assertThat(prisonerDetails.lastVoAllocatedDate).isEqualTo(prisonerSyncDto.createdDate)

    val expectedNegativeVisitOrderTotal = expectedNegativeVoCount + expectedNegativePvoCount
    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.filter { it.status == NegativeVisitOrderStatus.USED }.size).isEqualTo(expectedNegativeVisitOrderTotal)
    assertThat(negativeVisitOrders.filter { it.status == NegativeVisitOrderStatus.USED && it.type == NegativeVisitOrderType.NEGATIVE_VO }.size).isEqualTo(expectedNegativeVoCount)
    assertThat(negativeVisitOrders.filter { it.status == NegativeVisitOrderStatus.USED && it.type == NegativeVisitOrderType.NEGATIVE_PVO }.size).isEqualTo(expectedNegativePvoCount)

    val changeLogs = changeLogRepository.findAll()
    assertThat(changeLogs.size).isEqualTo(1)
    assertThat(changeLogs.first().prisonerId).isEqualTo(prisonerSyncDto.prisonerId)
    assertThat(changeLogs.first().changeType).isEqualTo(ChangeLogType.SYNC)
  }
}
