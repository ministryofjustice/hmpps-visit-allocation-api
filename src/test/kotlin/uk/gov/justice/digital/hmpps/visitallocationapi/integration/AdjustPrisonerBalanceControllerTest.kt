package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ManualBalanceAdjustmentValidationErrorResponse
import uk.gov.justice.digital.hmpps.visitallocationapi.config.ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_BALANCE
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceAdjustmentDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.AdjustmentReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.PrisonerBalanceAdjustmentValidationErrorCodes.PVO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.PrisonerBalanceAdjustmentValidationErrorCodes.VO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryAttributeType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus.ACCUMULATED
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus.AVAILABLE
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus.USED
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType.PVO
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType.VO
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.callPut
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import java.time.LocalDate

@DisplayName("Balance Controller tests to update a prisoner's VO and / or PVO balance - PUT $VO_BALANCE")
class AdjustPrisonerBalanceControllerTest : IntegrationTestBase() {

  companion object {
    const val PRISONER_ID = "AA123456"
  }

  @Test
  fun `when balance adjustment increases VO and PVO then both counts are updated`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(14)
    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate))
    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(5, 2, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, null, "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(5)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(2)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(7)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(5)
    assertThat(visitOrders.filter { it.type == PVO && it.status == AVAILABLE }.size).isEqualTo(2)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails.first().prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerDetails.first().lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    assertThat(prisonerDetails.first().lastPvoAllocatedDate).isEqualTo(lastPvoAllocationDate)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(5)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(2)
    assertThat(visitOrderHistory[0].comment).isEqualTo(null)
    assertThat(visitOrderHistory[0].userName).isEqualTo("test")

    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes[0].attributeType).isEqualTo(VisitOrderHistoryAttributeType.ADJUSTMENT_REASON_TYPE)
    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes[0].attributeValue).isEqualTo("GOVERNOR_ADJUSTMENT")
  }

  @Test
  fun `when balance adjustment increases only VO then only VO counts are updated`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = null
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 3, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(PVO, 4, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(4, null, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "VO balance adjusted", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(7)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(4)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(11)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(7)
    assertThat(visitOrders.filter { it.type == PVO && it.status == AVAILABLE }.size).isEqualTo(4)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails.first().prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerDetails.first().lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    assertThat(prisonerDetails.first().lastPvoAllocatedDate).isNull()

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(7)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(4)
    assertThat(visitOrderHistory[0].comment).isEqualTo("VO balance adjusted")
    assertThat(visitOrderHistory[0].userName).isEqualTo("test")

    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes[0].attributeType).isEqualTo(VisitOrderHistoryAttributeType.ADJUSTMENT_REASON_TYPE)
    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes[0].attributeValue).isEqualTo("GOVERNOR_ADJUSTMENT")
  }

  @Test
  fun `when balance adjustment increases only PVO then only PVO counts are updated`() {
    // Given
    val lastVoAllocationDate = LocalDate.now()
    val lastPvoAllocationDate = LocalDate.now()

    prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate))
    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(null, 10, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "PVO balance adjusted", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(0)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(10)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(10)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(0)
    assertThat(visitOrders.filter { it.type == PVO && it.status == AVAILABLE }.size).isEqualTo(10)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails.first().prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerDetails.first().lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    assertThat(prisonerDetails.first().lastPvoAllocatedDate).isEqualTo(lastPvoAllocationDate)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(0)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(10)
    assertThat(visitOrderHistory[0].comment).isEqualTo("PVO balance adjusted")
    assertThat(visitOrderHistory[0].userName).isEqualTo("test")

    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes[0].attributeType).isEqualTo(VisitOrderHistoryAttributeType.ADJUSTMENT_REASON_TYPE)
    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes[0].attributeValue).isEqualTo("GOVERNOR_ADJUSTMENT")
  }

  @Test
  fun `when adjustment comes in to decrease PVO but stays positive, correct amount of PVOs are expired`() {
    // Given
    val lastVoAllocationDate = LocalDate.now()
    val lastPvoAllocationDate = LocalDate.now()

    val prisoner = prisonerDetailsRepository.save(PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate))
    visitOrderRepository.saveAll(createVisitOrders(PVO, 13, prisoner))

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(null, -11, AdjustmentReasonType.OTHER, "Testing bug", "ADAVIES_GEN")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(0)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(2)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(13)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(0)
    assertThat(visitOrders.filter { it.type == PVO && it.status == AVAILABLE }.size).isEqualTo(2)
    assertThat(visitOrders.filter { it.type == PVO && it.status == USED }.size).isEqualTo(11)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails.first().prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerDetails.first().lastVoAllocatedDate).isEqualTo(lastVoAllocationDate)
    assertThat(prisonerDetails.first().lastPvoAllocatedDate).isEqualTo(lastPvoAllocationDate)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(0)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(2)
    assertThat(visitOrderHistory[0].comment).isEqualTo("Testing bug")
    assertThat(visitOrderHistory[0].userName).isEqualTo("ADAVIES_GEN")

    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes[0].attributeType).isEqualTo(VisitOrderHistoryAttributeType.ADJUSTMENT_REASON_TYPE)
    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes[0].attributeValue).isEqualTo("OTHER")
  }

  @Test
  fun `when balance adjustment decreases VO and PVO then both counts are updated`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(14)

    // prisoner has 11 VOs and 2 PVOs
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 11, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(PVO, 2, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(-5, -2, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "balance reduced", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(6)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(0)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(13)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(6)
    assertThat(visitOrders.filter { it.type == VO && it.status == USED }.size).isEqualTo(5)
    assertThat(visitOrders.filter { it.type == PVO && it.status == AVAILABLE }.size).isEqualTo(0)
    assertThat(visitOrders.filter { it.type == PVO && it.status == USED }.size).isEqualTo(2)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(6)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(0)
  }

  @Test
  fun `when balance adjustment decreases VO only then only VO count is updated`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(14)

    // prisoner has 11 VOs and 2 PVOs
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 26, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(PVO, 4, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(-5, 0, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "balance reduced", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(21)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(4)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(30)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(21)
    assertThat(visitOrders.filter { it.type == VO && it.status == USED }.size).isEqualTo(5)
    assertThat(visitOrders.filter { it.type == PVO && it.status == AVAILABLE }.size).isEqualTo(4)
    assertThat(visitOrders.filter { it.type == PVO && it.status == USED }.size).isEqualTo(0)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(21)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(4)
    assertThat(visitOrderHistory[0].comment).isEqualTo("balance reduced")
    assertThat(visitOrderHistory[0].userName).isEqualTo("test")

    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes[0].attributeType).isEqualTo(VisitOrderHistoryAttributeType.ADJUSTMENT_REASON_TYPE)
    assertThat(visitOrderHistory[0].visitOrderHistoryAttributes[0].attributeValue).isEqualTo("GOVERNOR_ADJUSTMENT")
  }

  @Test
  fun `when balance adjustment adds VOs and PVOs negative VOs are repaid first and then available Vos are added`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(14)

    // prisoner has 5 available VOs, 3 accumulated PVOs and 3 -ve PVOs
    // adding VOs should first repay -ve VOs before creating new ones
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 5, prisoner, AVAILABLE))
    prisoner.visitOrders.addAll(createVisitOrders(VO, 3, prisoner, ACCUMULATED))
    prisoner.negativeVisitOrders.addAll(createNegativeVisitOrders(VO, 3, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(PVO, 4, prisoner))
    prisoner.negativeVisitOrders.addAll(createNegativeVisitOrders(PVO, 2, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(6, 2, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "balance reduced", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(11)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(4)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(15)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(8)
    assertThat(visitOrders.filter { it.type == VO && it.status == ACCUMULATED }.size).isEqualTo(3)
    assertThat(visitOrders.filter { it.type == VO && it.status == USED }.size).isEqualTo(0)
    assertThat(visitOrders.filter { it.type == PVO && it.status == AVAILABLE }.size).isEqualTo(4)
    assertThat(visitOrders.filter { it.type == PVO && it.status == USED }.size).isEqualTo(0)

    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.size).isEqualTo(5)
    assertThat(negativeVisitOrders.filter { it.type == VO && it.status == NegativeVisitOrderStatus.USED }.size).isEqualTo(0)
    assertThat(negativeVisitOrders.filter { it.type == VO && it.status == NegativeVisitOrderStatus.REPAID }.size).isEqualTo(3)
    assertThat(negativeVisitOrders.filter { it.type == PVO && it.status == NegativeVisitOrderStatus.USED }.size).isEqualTo(0)
    assertThat(negativeVisitOrders.filter { it.type == PVO && it.status == NegativeVisitOrderStatus.REPAID }.size).isEqualTo(2)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(11)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(4)
  }

  @Test
  fun `when balance adjustment adds VOs and PVOs negative VOs are repaid even if total stays below 0`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(14)

    // prisoner has 0 available VOs, 0 accumulated PVOs and 4 -ve PVOs
    // adding VOs should repay -ve VOs even if balance does not go above 0
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 2, prisoner, USED))
    prisoner.negativeVisitOrders.addAll(createNegativeVisitOrders(VO, 4, prisoner))
    prisoner.negativeVisitOrders.addAll(createNegativeVisitOrders(PVO, 2, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(1, 2, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "balance reduced", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(-3)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(0)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(2)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(0)
    assertThat(visitOrders.filter { it.type == VO && it.status == ACCUMULATED }.size).isEqualTo(0)
    assertThat(visitOrders.filter { it.type == VO && it.status == USED }.size).isEqualTo(2)
    assertThat(visitOrders.filter { it.type == PVO && it.status == AVAILABLE }.size).isEqualTo(0)
    assertThat(visitOrders.filter { it.type == PVO && it.status == USED }.size).isEqualTo(0)

    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.size).isEqualTo(6)
    assertThat(negativeVisitOrders.filter { it.type == VO && it.status == NegativeVisitOrderStatus.USED }.size).isEqualTo(3)
    assertThat(negativeVisitOrders.filter { it.type == VO && it.status == NegativeVisitOrderStatus.REPAID }.size).isEqualTo(1)
    assertThat(negativeVisitOrders.filter { it.type == PVO && it.status == NegativeVisitOrderStatus.USED }.size).isEqualTo(0)
    assertThat(negativeVisitOrders.filter { it.type == PVO && it.status == NegativeVisitOrderStatus.REPAID }.size).isEqualTo(2)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(-3)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(0)
  }

  @Test
  fun `when balance adjustment decreases allocated VOs first and then available Vos only then VO count is updated`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(14)

    // prisoner has 11 VOs and 2 PVOs
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 21, prisoner, AVAILABLE))
    prisoner.visitOrders.addAll(createVisitOrders(VO, 3, prisoner, ACCUMULATED))
    prisoner.visitOrders.addAll(createVisitOrders(PVO, 4, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(-5, 0, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "balance reduced", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(19)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(4)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(28)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(19)
    assertThat(visitOrders.filter { it.type == VO && it.status == ACCUMULATED }.size).isEqualTo(0)
    assertThat(visitOrders.filter { it.type == VO && it.status == USED }.size).isEqualTo(5)
    assertThat(visitOrders.filter { it.type == PVO && it.status == AVAILABLE }.size).isEqualTo(4)
    assertThat(visitOrders.filter { it.type == PVO && it.status == USED }.size).isEqualTo(0)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(19)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(4)
  }

  @Test
  fun `when balance adjustment decreases VOs no available VOs are set to USED if  accumulated count is more`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(14)

    // prisoner has 11 VOs and 2 PVOs
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 4, prisoner, AVAILABLE))
    prisoner.visitOrders.addAll(createVisitOrders(VO, 3, prisoner, ACCUMULATED))
    prisonerDetailsRepository.save(prisoner)

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(-2, 0, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "balance reduced", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(5)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(0)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(7)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(4)
    assertThat(visitOrders.filter { it.type == VO && it.status == ACCUMULATED }.size).isEqualTo(1)
    assertThat(visitOrders.filter { it.type == VO && it.status == USED }.size).isEqualTo(2)
    assertThat(visitOrders.filter { it.type == PVO }.size).isEqualTo(0)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(5)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(0)
  }

  @Test
  fun `when balance adjustment decreases PVO only then only PVO count is updated`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(14)

    // prisoner has 11 VOs and 2 PVOs
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 8, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(PVO, 6, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(null, -4, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "balance reduced", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isOk
    val prisonerBalance = getVoBalanceResponse(responseSpec)
    assertThat(prisonerBalance.prisonerId).isEqualTo(PRISONER_ID)
    assertThat(prisonerBalance.voBalance).isEqualTo(8)
    assertThat(prisonerBalance.pvoBalance).isEqualTo(2)

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(14)
    assertThat(visitOrders.filter { it.type == VO && it.status == AVAILABLE }.size).isEqualTo(8)
    assertThat(visitOrders.filter { it.type == PVO && it.status == AVAILABLE }.size).isEqualTo(2)
    assertThat(visitOrders.filter { it.type == PVO && it.status == USED }.size).isEqualTo(4)

    val changeLog = changeLogRepository.findAll()
    assertThat(changeLog.size).isEqualTo(1)
    assertThat(changeLog.first().changeType).isEqualTo(ChangeLogType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)

    val visitOrderHistory = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistory.size).isEqualTo(1)
    assertThat(visitOrderHistory[0].type).isEqualTo(VisitOrderHistoryType.MANUAL_PRISONER_BALANCE_ADJUSTMENT)
    assertThat(visitOrderHistory[0].voBalance).isEqualTo(8)
    assertThat(visitOrderHistory[0].pvoBalance).isEqualTo(2)
  }

  @Test
  fun `when balance adjustment takes VO limit above max limit an exception is thrown`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(14)

    // prisoner has 24 VOs and 6 PVOs
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 24, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(PVO, 6, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(4, 6, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "balance reduced", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
    val errorResponse = getValidationErrorResponse(responseSpec)
    assertThat(errorResponse.status).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value())
    assertThat(errorResponse.validationErrors.size).isEqualTo(1)
    assertThat(errorResponse.validationErrors).containsAll(listOf(VO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX))
    assertThat(errorResponse.userMessage).isEqualTo("Validation for balance adjustment failed")
    assertThat(errorResponse.developerMessage).isEqualTo("Validation for balance adjustment failed: VO count after adjustment will take it past max allowed")
  }

  @Test
  fun `when balance adjustment takes PVO limit below zero an exception is thrown`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(3)

    // prisoner has 24 VOs and 6 PVOs
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 4, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(PVO, 3, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(-4, -4, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "balance reduced", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
    val errorResponse = getValidationErrorResponse(responseSpec)
    assertThat(errorResponse.status).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value())
    assertThat(errorResponse.validationErrors.size).isEqualTo(1)
    assertThat(errorResponse.validationErrors).containsAll(listOf(PVO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO))
    assertThat(errorResponse.userMessage).isEqualTo("Validation for balance adjustment failed")
    assertThat(errorResponse.developerMessage).isEqualTo("Validation for balance adjustment failed: PVO count after adjustment will take it below zero")
  }

  @Test
  fun `when balance adjustment has multiple issues an exception is thrown with multiple errors`() {
    // Given
    val lastVoAllocationDate = LocalDate.now().minusDays(7)
    val lastPvoAllocationDate = LocalDate.now().minusDays(3)

    // prisoner has 24 VOs and 6 PVOs
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = lastVoAllocationDate, lastPvoAllocatedDate = lastPvoAllocationDate)
    prisoner.visitOrders.addAll(createVisitOrders(VO, 23, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(PVO, 3, prisoner))
    prisonerDetailsRepository.save(prisoner)

    // balance adjustment will take vo limit above 26 and PVO limit below 0
    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(4, -4, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "balance reduced", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
    val errorResponse = getValidationErrorResponse(responseSpec)
    assertThat(errorResponse.status).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value())
    assertThat(errorResponse.validationErrors.size).isEqualTo(2)
    assertThat(errorResponse.validationErrors).containsAll(listOf(VO_TOTAL_POST_ADJUSTMENT_ABOVE_MAX, PVO_TOTAL_POST_ADJUSTMENT_BELOW_ZERO))
    assertThat(errorResponse.userMessage).isEqualTo("Validation for balance adjustment failed")
    assertThat(errorResponse.developerMessage).isEqualTo("Validation for balance adjustment failed: VO count after adjustment will take it past max allowed, PVO count after adjustment will take it below zero")
  }

  @Test
  fun `when request to get an unknown prisoner, then status 404 NOT_FOUND is returned`() {
    // Given
    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(null, 10, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "PVO balance adjusted", "test")

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, setAuthorisation(roles = listOf(ROLE_VISIT_ALLOCATION_API__VSIP_ORCHESTRATION_API)))

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val balanceAdjustmentDto = PrisonerBalanceAdjustmentDto(null, 10, AdjustmentReasonType.GOVERNOR_ADJUSTMENT, "PVO balance adjusted", "test")
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())

    // When
    val responseSpec = callVisitAllocationPrisonerBalanceEndpoint(PRISONER_ID, balanceAdjustmentDto, webTestClient, incorrectAuthHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given no auth token

    // When
    val responseSpec = webTestClient.put().uri(getPrisonerBalanceUrl(PRISONER_ID)).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callVisitAllocationPrisonerBalanceEndpoint(
    prisonerId: String,
    balanceAdjustmentDto: PrisonerBalanceAdjustmentDto,
    webTestClient: WebTestClient,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callPut(
    balanceAdjustmentDto,
    webTestClient,
    getPrisonerBalanceUrl(prisonerId),
    authHttpHeaders,
  )

  private fun getPrisonerBalanceUrl(prisonerId: String): String = VO_BALANCE.replace("{prisonerId}", prisonerId)

  private fun getVoBalanceResponse(responseSpec: ResponseSpec): PrisonerBalanceDto = TestObjectMapper.mapper.readValue(responseSpec.expectBody().returnResult().responseBody, PrisonerBalanceDto::class.java)

  private fun getValidationErrorResponse(responseSpec: ResponseSpec): ManualBalanceAdjustmentValidationErrorResponse = TestObjectMapper.mapper.readValue(responseSpec.expectBody().returnResult().responseBody, ManualBalanceAdjustmentValidationErrorResponse::class.java)
}
