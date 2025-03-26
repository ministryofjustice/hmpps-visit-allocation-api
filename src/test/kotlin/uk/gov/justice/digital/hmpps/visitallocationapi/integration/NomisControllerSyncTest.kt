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
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__NOMIS_API
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
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import java.time.LocalDate

@DisplayName("NomisController sync tests - $VO_PRISONER_SYNC")
class NomisControllerSyncTest : IntegrationTestBase() {

  companion object {
    const val PRISONER_ID = "AA123456"
  }

  /**
   * Scenario 1: Existing Prisoner who has a positive balance which is in sync, has an increase to their balance. DPS syncs successfully.
   */
  @Test
  fun `when an existing prisoner with a positive balance increases, then DPS service successfully syncs`() {
    // Given
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 5)
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 2)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 5,
      changeToVoBalance = 1,
      oldPvoBalance = 2,
      changeToPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 6, expectedPvoCount = 3, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 2: Existing Prisoner who has a negative balance which is in sync, has a decrease to their balance. DPS syncs successfully.
   */
  @Test
  fun `when an existing prisoner with a negative balance decreases, then DPS service successfully syncs`() {
    // Given
    entityHelper.createAndSaveNegativeVisitOrders(prisonerId = PRISONER_ID, NegativeVisitOrderType.NEGATIVE_VO, 5)
    entityHelper.createAndSaveNegativeVisitOrders(prisonerId = PRISONER_ID, NegativeVisitOrderType.NEGATIVE_PVO, 2)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = -5,
      changeToVoBalance = -1,
      oldPvoBalance = -2,
      changeToPvoBalance = -1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 0, expectedPvoCount = 0, expectedNegativeVoCount = 6, expectedNegativePvoCount = 3)
    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 3: Existing Prisoner who has a positive balance which is in sync, has a decrease to their balance which puts them into negative.
   * DPS syncs successfully. All positive visit orders are expired and new negative visit orders are created with status USED.
   */
  @Test
  fun `when an existing prisoner with a positive balance decreases below zero, then DPS service successfully syncs`() {
    // Given
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 1)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 2,
      changeToVoBalance = -3,
      oldPvoBalance = 1,
      changeToPvoBalance = -2,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 0, expectedPvoCount = 0, expectedNegativeVoCount = 1, expectedNegativePvoCount = 1)
    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 4: Existing Prisoner who has a negative balance which is in sync, has an increase to their balance which puts them into positive.
   * DPS syncs successfully. All negative visit orders are repaid and new visit orders are created with status AVAILABLE.
   */
  @Test
  fun `when an existing prisoner with a negative balance increases above zero, then DPS service successfully syncs`() {
    // Given
    entityHelper.createAndSaveNegativeVisitOrders(prisonerId = PRISONER_ID, NegativeVisitOrderType.NEGATIVE_VO, 2)
    entityHelper.createAndSaveNegativeVisitOrders(prisonerId = PRISONER_ID, NegativeVisitOrderType.NEGATIVE_PVO, 1)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = -2,
      changeToVoBalance = 3,
      oldPvoBalance = -1,
      changeToPvoBalance = 2,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 1, expectedPvoCount = 1, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 5: Existing Prisoner who has a positive balance which is out of sync (DPS is higher), has an increase to their balance, DPS syncs successfully.
   */
  @Test
  fun `when an existing prisoner with an out of sync (DPS higher) positive balance increases, then DPS service successfully syncs`() {
    // Given
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 6)
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 3)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 5,
      changeToVoBalance = 1,
      oldPvoBalance = 2,
      changeToPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 6, expectedPvoCount = 3, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verify(telemetryClientService, times(2)).trackEvent(any(), any())
  }

  /**
   * Scenario 6: Existing Prisoner who has a positive balance which is out of sync (NOMIS is higher), has an increase to their balance, DPS syncs successfully.
   */
  @Test
  fun `when an existing prisoner with an out of sync (NOMIS higher) positive balance increases, then DPS service successfully syncs`() {
    // Given
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 4)
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 1)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 5,
      changeToVoBalance = 1,
      oldPvoBalance = 2,
      changeToPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 6, expectedPvoCount = 3, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verify(telemetryClientService, times(2)).trackEvent(any(), any())
  }

  /**
   * Scenario 7: New Prisoner who has a zero balance which is in sync, has an increase to their balance, DPS syncs successfully.
   */
  @Test
  fun `when a new prisoner with a zero balance increases, then DPS service successfully syncs`() {
    // Given
    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 0,
      changeToVoBalance = 1,
      oldPvoBalance = 0,
      changeToPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 1, expectedPvoCount = 1, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 8: New Prisoner who has a zero balance which is in sync, has a decrease to their balance, DPS syncs successfully.
   */
  @Test
  fun `when a new prisoner with a zero balance decreases, then DPS service successfully syncs`() {
    // Given
    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 0,
      changeToVoBalance = -1,
      oldPvoBalance = 0,
      changeToPvoBalance = -1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 0, expectedPvoCount = 0, expectedNegativeVoCount = 1, expectedNegativePvoCount = 1)
    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 9: Existing Prisoner who has a positive balance which is out of sync (DPS higher), has a decrease to their balance which puts them into negative.
   * DPS syncs successfully. All visit orders are expired and new negative visit orders are created with status USED.
   */
  @Test
  fun `when an existing prisoner with an out of sync (DPS higher) and a positive balance decreases below zero, then DPS service successfully syncs`() {
    // Given
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 2)
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 1)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 1,
      changeToVoBalance = -3,
      oldPvoBalance = 0,
      changeToPvoBalance = -2,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 0, expectedPvoCount = 0, expectedNegativeVoCount = 2, expectedNegativePvoCount = 2)
    verify(telemetryClientService, times(2)).trackEvent(any(), any())
  }

  /**
   * Scenario 10: Existing Prisoner who has a negative balance which is out of sync (NOMIS higher), has an increase to their balance which puts them into positive.
   * DPS syncs successfully. All negative visit orders are repaid and new visit orders are created with status AVAILABLE.
   */
  @Test
  fun `when an existing prisoner with an out of sync (NOMIS higher) and a negative balance increases above zero, then DPS service successfully syncs`() {
    // Given
    entityHelper.createAndSaveNegativeVisitOrders(prisonerId = PRISONER_ID, NegativeVisitOrderType.NEGATIVE_VO, 1)
    entityHelper.createAndSaveNegativeVisitOrders(prisonerId = PRISONER_ID, NegativeVisitOrderType.NEGATIVE_PVO, 1)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = -2,
      changeToVoBalance = 3,
      oldPvoBalance = -2,
      changeToPvoBalance = 3,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 1, expectedPvoCount = 1, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verify(telemetryClientService, times(2)).trackEvent(any(), any())
  }

  /**
   * Scenario 11: Existing Prisoner with no change to VO balance, then VO processing is skipped.
   */
  @Test
  fun `when an existing prisoner with only a PVO balance change, then VO processing is skipped`() {
    // Given
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 5)
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 2)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = null,
      changeToVoBalance = null,
      oldPvoBalance = 2,
      changeToPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 5, expectedPvoCount = 3, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verifyNoInteractions(telemetryClientService)
  }

  /**
   * Scenario 12: Existing Prisoner with no change to PVO balance, then PVO processing is skipped.
   */
  @Test
  fun `when an existing prisoner with only a VO balance change, then PVO processing is skipped`() {
    // Given
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.VO, 5)
    entityHelper.createAndSaveVisitOrders(prisonerId = PRISONER_ID, VisitOrderType.PVO, 2)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null))

    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 5,
      changeToVoBalance = 1,
      oldPvoBalance = null,
      changeToPvoBalance = null,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isOk
    assertSyncResults(prisonerSyncDto = prisonerSyncDto, expectedVoCount = 6, expectedPvoCount = 2, expectedNegativeVoCount = 0, expectedNegativePvoCount = 0)
    verifyNoInteractions(telemetryClientService)
  }

  @Test
  fun `when changeToVoBalance is given but oldVoBalance is null, return a 400 Bad Request`() {
    // Given
    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = null,
      changeToVoBalance = 1,
      oldPvoBalance = 2,
      changeToPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when changeToPvoBalance is given but oldPvoBalance is null, return a 400 Bad Request`() {
    // Given
    val prisonerSyncDto = createSyncRequest(
      prisonerId = PRISONER_ID,
      oldVoBalance = 2,
      changeToVoBalance = 1,
      oldPvoBalance = null,
      changeToPvoBalance = 1,
    )

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when request body validation fails then 400 bad request is returned`() {
    // Given
    val prisonerSyncDto = VisitAllocationPrisonerSyncDto("", 5, 1, 2, 0, LocalDate.now().minusDays(1), AdjustmentReasonCode.VO_ISSUE, ChangeLogSource.SYSTEM, "issued vo")

    // When
    val responseSpec = callVisitAllocationSyncEndpoint(webTestClient, prisonerSyncDto, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__NOMIS_API)))

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
    oldVoBalance: Int? = null,
    changeToVoBalance: Int? = null,
    oldPvoBalance: Int? = null,
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
