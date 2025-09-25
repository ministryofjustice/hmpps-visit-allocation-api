package uk.gov.justice.digital.hmpps.visitallocationapi.integration.events

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonerIncentivesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.IncentivesMockExtension.Companion.incentivesMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationPrisonJob
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJob
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class VisitAllocationByPrisonJobSqsTest : EventsIntegrationTestBase() {
  companion object {
    const val PRISON_CODE = "MDI"
    val prisoner1 = PrisonerDto(prisonerId = "ABC121", prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = "HEI")
    val prisoner2 = PrisonerDto(prisonerId = "ABC122", prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = "HEI")
    val prisoner3 = PrisonerDto(prisonerId = "ABC123", prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = "HEI")
    val prisoner4 = PrisonerDto(prisonerId = "ABC124", prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = "HEI")
  }

  /**
   * Scenario - Allocation: Visit allocation job is run, and all prisoners are allocated visit orders (VO / PVO).
   * Prisoner1 - Standard incentive, Gets 1 VO, 0 PVO.
   * Prisoner2 - Enhanced incentive, Gets 2 VO, 1 PVO.
   * Prisoner3 - Enhanced2 incentive, Gets 3 VO, 2 PVOs.
   */
  @Test
  fun `when visit allocation job run for a prison then processMessage is called and visit orders are created for convicted prisoners`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner2.prisonerId, createPrisonerDto(prisonerId = prisoner2.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner3.prisonerId, createPrisonerDto(prisonerId = prisoner3.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner2.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner3.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any()) }

    val visitOrders = visitOrderRepository.findAll()

    assertThat(visitOrders.size).isEqualTo(9)

    // as prisoner1 is STD he should only get 1 VO and 0 PVOs
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 1)
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)

    // as prisoner2 is ENH he should get 2 VOs and 1 PVO
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 2)
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 1)

    // as prisoner3 is ENH2 he should get 3 VOs and 2 PVO
    assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 3)
    assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 2)

    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateStartTimestamp(any(), any(), any())
    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any())
    verify(snsService, times(3)).sendPrisonAllocationAdjustmentCreatedEvent(any())
    val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
    assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 3, processedPrisoners = 3, failedOrSkippedPrisoners = 0)
  }

  /**
   * Scenario - Allocation: Visit allocation job is run with lowercase, and all prisoners are still allocated visit orders (VO / PVO).
   * Prisoner1 - Standard incentive, Gets 1 VO, 0 PVO.
   * Prisoner2 - Enhanced incentive, Gets 2 VO, 1 PVO.
   * Prisoner3 - Enhanced2 incentive, Gets 3 VO, 2 PVOs.
   */
  @Test
  fun `when visit allocation job run for a prison with lowercase then processMessage is called with upper case prison code and visit orders are created for convicted prisoners`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"

    // prison code sent with lower case
    val prisonCode = PRISON_CODE.lowercase()
    val event = VisitAllocationEventJob(allocationJobReference, prisonCode)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))
    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner2.prisonerId, createPrisonerDto(prisonerId = prisoner2.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner3.prisonerId, createPrisonerDto(prisonerId = prisoner3.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner2.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner3.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any()) }

    val visitOrders = visitOrderRepository.findAll()

    assertThat(visitOrders.size).isEqualTo(9)

    // as prisoner1 is STD he should only get 1 VO and 0 PVOs
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 1)
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)

    // as prisoner2 is ENH he should get 2 VOs and 1 PVO
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 2)
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 1)

    // as prisoner3 is ENH2 he should get 3 VOs and 2 PVO
    assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 3)
    assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 2)

    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateStartTimestamp(any(), any(), any())
    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any())
    verify(snsService, times(3)).sendPrisonAllocationAdjustmentCreatedEvent(any())

    val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
    assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 3, processedPrisoners = 3, failedOrSkippedPrisoners = 0)
  }

  /**
   * Scenario - Allocation: Visit allocation job is run but getAllPrisonIncentiveLevels fails, and all prisoners are still allocated visit orders (VO / PVO).
   * Prisoner1 - Standard incentive, Gets 1 VO, 0 PVO.
   * Prisoner2 - Enhanced incentive, Gets 2 VO, 1 PVO.
   * Prisoner3 - Enhanced2 incentive, Gets 3 VO, 2 PVOs.
   */
  @Test
  fun `when visit allocation job is run for a prison and getAllPrisonIncentiveLevels returns an empty list the prison is still processed and visit orders are created for convicted prisoners`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))
    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner2.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner3.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))

    // stubGetAllPrisonIncentiveLevels returns an empty list
    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      emptyList(),
    )

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner2.prisonerId, createPrisonerDto(prisonerId = prisoner2.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner3.prisonerId, createPrisonerDto(prisonerId = prisoner3.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonIncentiveLevelsByLevelCode(prisonId = PRISON_CODE, levelCode = "STD", prisonIncentiveAmountsDto = PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"))
    incentivesMockServer.stubGetPrisonIncentiveLevelsByLevelCode(prisonId = PRISON_CODE, levelCode = "ENH", prisonIncentiveAmountsDto = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"))
    incentivesMockServer.stubGetPrisonIncentiveLevelsByLevelCode(prisonId = PRISON_CODE, levelCode = "ENH2", prisonIncentiveAmountsDto = PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"))

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any()) }

    val visitOrders = visitOrderRepository.findAll()

    assertThat(visitOrders.size).isEqualTo(9)

    // as prisoner1 is STD he should only get 1 VO and 0 PVOs
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 1)
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)

    // as prisoner2 is ENH he should get 2 VOs and 1 PVO
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 2)
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 1)

    // as prisoner3 is ENH2 he should get 3 VOs and 2 PVO
    assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 3)
    assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 2)

    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateStartTimestamp(any(), any(), any())
    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any())
    verify(snsService, times(3)).sendPrisonAllocationAdjustmentCreatedEvent(any())

    val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
    assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 3, processedPrisoners = 3, failedOrSkippedPrisoners = 0)
  }

  /**
   * Scenario - Allocation with negative balances: Visit allocation job is run, and all prisoners with negative balances are repaid (VO / PVO).
   * Prisoner1 - Start balance (VO=-2, PVO=-1) - Standard incentive, Gets 1 VO, 0 PVO. End balance (VO=-1, PVO=-1)
   * Prisoner2 - Start balance (VO=-1, PVO=-1) - Enhanced2 incentive, Gets 3 VO, 2 PVOs. End balance (VO=2, PVO=1)
   */
  @Test
  fun `when visit allocation job run for a prison with prisoners in a negative balance, then processMessage is called and negative visit orders are repaid for convicted prisoners`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // Negative balance for prisoner1
    entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = prisoner1.prisonerId, LocalDate.now().minusDays(14), null))
    entityHelper.createAndSaveNegativeVisitOrders(prisoner1.prisonerId, VisitOrderType.VO, 2)
    entityHelper.createAndSaveNegativeVisitOrders(prisoner1.prisonerId, VisitOrderType.PVO, 1)

    // Negative balance for prisoner2
    entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = prisoner2.prisonerId, LocalDate.now().minusDays(14), null))
    entityHelper.createAndSaveNegativeVisitOrders(prisoner2.prisonerId, VisitOrderType.VO, 1)
    entityHelper.createAndSaveNegativeVisitOrders(prisoner2.prisonerId, VisitOrderType.PVO, 1)

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner2.prisonerId, createPrisonerDto(prisonerId = prisoner2.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner2.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any()) }

    val visitOrders = visitOrderRepository.findAll()
    val negativeVisitOrders = negativeVisitOrderRepository.findAll()

    assertThat(visitOrders.size).isEqualTo(3)

    // Prisoner1 should have 1 Negative_VO repaid, and the rest remain unchanged
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 0)
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)
    assertNegativeVisitOrdersAssignedBy(negativeVisitOrders, prisoner1.prisonerId, VisitOrderType.VO, NegativeVisitOrderStatus.USED, 1)
    assertNegativeVisitOrdersAssignedBy(negativeVisitOrders, prisoner1.prisonerId, VisitOrderType.VO, NegativeVisitOrderStatus.REPAID, 1)
    assertNegativeVisitOrdersAssignedBy(negativeVisitOrders, prisoner1.prisonerId, VisitOrderType.PVO, NegativeVisitOrderStatus.USED, 1)

    // Prisoner2 should have all Negative_VOs / Negative_PVOs repaid, and 2 VOs and 1 PVO.
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 2)
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 1)
    assertNegativeVisitOrdersAssignedBy(negativeVisitOrders, prisoner2.prisonerId, VisitOrderType.VO, NegativeVisitOrderStatus.USED, 0)
    assertNegativeVisitOrdersAssignedBy(negativeVisitOrders, prisoner2.prisonerId, VisitOrderType.PVO, NegativeVisitOrderStatus.USED, 0)
    assertNegativeVisitOrdersAssignedBy(negativeVisitOrders, prisoner2.prisonerId, VisitOrderType.VO, NegativeVisitOrderStatus.REPAID, 1)
    assertNegativeVisitOrdersAssignedBy(negativeVisitOrders, prisoner2.prisonerId, VisitOrderType.PVO, NegativeVisitOrderStatus.REPAID, 1)

    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateStartTimestamp(any(), any(), any())
    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any())
    verify(snsService, times(2)).sendPrisonAllocationAdjustmentCreatedEvent(any())

    val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
    assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 2, processedPrisoners = 2, failedOrSkippedPrisoners = 0)
  }

  /**
   * Scenario - Accumulation: Visit allocation job is run and accumulation occurs.
   * Prisoner1 - Has no existing VOs - No accumulation.
   * Prisoner2 - Has 2 VOs older than 28days - 2 VOs are accumulated.
   * Prisoner3 - Has 2 VOs less than 28days old - No accumulation.
   */
  @Test
  fun `when visit allocation job run for a prison then processMessage is called and visit orders are accumulated for convicted prisoners`() {
    // Given - Some prisoners have pre-existing VOs, and a message is sent to start allocation job for prison
    entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = prisoner2.prisonerId, LocalDate.now().minusDays(14), null))
    entityHelper.createAndSaveVisitOrders(prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(29).atStartOfDay(), 2)

    entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = prisoner3.prisonerId, LocalDate.now().minusDays(14), null))
    entityHelper.createAndSaveVisitOrders(prisoner3.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(14).atStartOfDay(), 2)

    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner2.prisonerId, createPrisonerDto(prisonerId = prisoner2.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner3.prisonerId, createPrisonerDto(prisonerId = prisoner3.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner2.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner3.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    // Then
    Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted {
        // Then
        await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
        await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
        await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
        val visitOrders = visitOrderRepository.findAll()

        assertThat(visitOrders.size).isEqualTo(13)

        // no existing VOs, so only gets new allocation.
        assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 1)
        assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)

        // 2 existing VOs (now accumulated), and 2 new VOs (available).
        assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 2)
        assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.ACCUMULATED, 2)
        assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 1)

        // 2 existing VO (available), and 3 new VOs (available).
        assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 5)
        assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 2)

        val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
        assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 3, processedPrisoners = 3, failedOrSkippedPrisoners = 0)

        verify(snsService, times(3)).sendPrisonAllocationAdjustmentCreatedEvent(any())
      }
  }

  /**
   * Scenario - Expiration: Visit allocation job is run and expiration occurs.
   * VOs - All accumulated VOs more than 26 accumulated get expired.
   * PVOs - All available PVOs more than 28days old get expired.
   *
   * Prisoner1 - Has no accumulated VOs - No expiration.
   * Prisoner2 - Has 28 accumulated VOs - 2 accumulated VOs are expired.
   * Prisoner3 - Has 2 PVOs older than 28days - 2 PVOs are expired.
   */
  @Test
  fun `when visit allocation job run for a prison then processMessage is called and visit orders are expired for convicted prisoners`() {
    // Given - Some prisoners have pre-existing VOs / PVOs, and a message is sent to start allocation job for prison
    entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = prisoner2.prisonerId, LocalDate.now().minusDays(1), null))
    entityHelper.createAndSaveVisitOrders(prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.ACCUMULATED, LocalDate.now().minusDays(1).atStartOfDay(), 28)

    entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = prisoner3.prisonerId, LocalDate.now().minusDays(14), LocalDate.now().minusDays(29)))
    entityHelper.createAndSaveVisitOrders(prisoner3.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(29).atStartOfDay(), 2)

    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner2.prisonerId, createPrisonerDto(prisonerId = prisoner2.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner3.prisonerId, createPrisonerDto(prisonerId = prisoner3.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner2.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner3.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    // Then
    Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted {
        // Then
        await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
        await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
        await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
        val visitOrders = visitOrderRepository.findAll()

        // no existing VOs, so only gets new allocation.
        assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 1)
        assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)

        // 28 existing VOs (26 accumulated), and 2 expired VOs (expired).
        assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.ACCUMULATED, 26)
        assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.EXPIRED, 2)
        assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)

        // 3 new VOs (available), 2 new PVOs (available), and 2 existing PVOs (expired).
        assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 3)
        assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 2)
        assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.PVO, VisitOrderStatus.EXPIRED, 2)
        val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
        assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 3, processedPrisoners = 3, failedOrSkippedPrisoners = 0)

        assertThat(visitOrders.size).isEqualTo(36)

        verify(snsService, times(3)).sendPrisonAllocationAdjustmentCreatedEvent(any())
      }
  }

  /**
   * Scenario - Allocation: Visit allocation job is run, and if even one fails with a RuntimeException the process continues.
   * Prisoner1 - Standard incentive, Gets 1 VO, 0 PVO.
   * Prisoner2 - Gets no VOs - as the call to incentives fails.
   * Prisoner3 - Enhanced2 incentive, Gets 3 VO, 2 PVOs.
   * Prisoner4 - Enhanced2 incentive, Gets 3 VO, 2 PVOs.
   */
  @Test
  fun `when visit allocation job run for a prison and processing for one of the prisoners fails with a 404 error the process continues`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3, prisoner4)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner2.prisonerId, createPrisonerDto(prisonerId = prisoner2.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner3.prisonerId, createPrisonerDto(prisonerId = prisoner3.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner4.prisonerId, createPrisonerDto(prisonerId = prisoner4.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))

    // a 404 is being returned for prisoner 2
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner2.prisonerId, null)
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner3.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner4.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any()) }

    val visitOrders = visitOrderRepository.findAll()

    assertThat(visitOrders.size).isEqualTo(11)

    // as prisoner1 is STD he should only get 1 VO and 0 PVOs
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 1)
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)

    // as prisoner2 returned a 404 - he gets no VOs assigned and the prisoner is put on retry queue
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 0)
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)
    verify(visitAllocationPrisonerRetrySqsService, times(1)).sendToVisitAllocationPrisonerRetryQueue(allocationJobReference, prisoner2.prisonerId)

    // as prisoner3 and prisoner4 are  ENH2 they should get 3 VOs and 2 PVOs each
    assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 3)
    assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 2)

    assertVisitOrdersAssignedBy(visitOrders, prisoner4.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 3)
    assertVisitOrdersAssignedBy(visitOrders, prisoner4.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 2)

    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateStartTimestamp(any(), any(), any())
    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any())
    verify(snsService, times(3)).sendPrisonAllocationAdjustmentCreatedEvent(any())

    val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
    assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 4, processedPrisoners = 3, failedOrSkippedPrisoners = 1)
  }

  /**
   * Scenario - Allocation: Visit allocation job is run, and if even one fails with a RuntimeException the process continues.
   * Prisoner1 - Standard incentive, Gets 1 VO, 0 PVO.
   * Prisoner2 - Gets no VOs - as the call to incentives fails.
   * Prisoner3 - Enhanced2 incentive, Gets 3 VO, 2 PVOs.
   * Prisoner4 - Enhanced2 incentive, Gets 3 VO, 2 PVOs.
   */
  @Test
  fun `when visit allocation job run for a prison and processing for one of the prisoners fails with a 500 error the process continues`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3, prisoner4)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner2.prisonerId, createPrisonerDto(prisonerId = prisoner2.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner3.prisonerId, createPrisonerDto(prisonerId = prisoner3.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner4.prisonerId, createPrisonerDto(prisonerId = prisoner4.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))

    // a 500 error is being returned for prisoner 2
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner2.prisonerId, null, HttpStatus.INTERNAL_SERVER_ERROR)
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner3.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner4.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any()) }

    val visitOrders = visitOrderRepository.findAll()

    assertThat(visitOrders.size).isEqualTo(11)

    // as prisoner1 is STD he should only get 1 VO and 0 PVOs
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 1)
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)

    // as prisoner2 returned a 500 - he gets no VOs assigned and the prisoner is put on retry queue
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 0)
    assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 0)
    verify(visitAllocationPrisonerRetrySqsService, times(1)).sendToVisitAllocationPrisonerRetryQueue(allocationJobReference, prisoner2.prisonerId)

    // as prisoner3 and prisoner4 are  ENH2 they should get 3 VOs and 2 PVOs each
    assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 3)
    assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 2)

    assertVisitOrdersAssignedBy(visitOrders, prisoner4.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 3)
    assertVisitOrdersAssignedBy(visitOrders, prisoner4.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, 2)

    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateStartTimestamp(any(), any(), any())
    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any())
    verify(snsService, times(3)).sendPrisonAllocationAdjustmentCreatedEvent(any())

    val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
    assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 4, processedPrisoners = 3, failedOrSkippedPrisoners = 1)
  }

  /**
   * Scenario - Allocation: Visit allocation job is run, but call to get convicted prisoners fail.
   */
  @Test
  fun `when call to get convicted prisoners fails with a status of NOT_FOUND then end time and failure message are populated`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // When
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, null, HttpStatus.NOT_FOUND)
    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)
    Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
      await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
      val visitOrders = visitOrderRepository.findAll()

      assertThat(visitOrders.size).isEqualTo(0)
      val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
      assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], failureMessage = "failed to get convicted prisoners by prisonId - $PRISON_CODE", convictedPrisoners = null, processedPrisoners = null, failedOrSkippedPrisoners = null)
    }
  }

  /**
   * Scenario - Allocation with maximum balances: Visit allocation job is run, prisoner already has maximum VOs of 26, no extra VOs are generated.
   * Prisoner1 - Start balance (VO=26, PVO=0) - Standard incentive, Gets 1 VO, 0 PVO. End balance (VO=26, PVO=0)
   */
  @Test
  fun `when visit allocation job run for a prison with prisoner at maximum vo balance, then processMessage is called and no VOs are generated`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // Maximum balance for prisoner1
    entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = prisoner1.prisonerId, LocalDate.now().minusDays(14), null))
    entityHelper.createAndSaveVisitOrders(prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, LocalDateTime.now(), 2)
    entityHelper.createAndSaveVisitOrders(prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.ACCUMULATED, LocalDateTime.now(), 24)

    // When
    val convictedPrisoners = listOf(prisoner1)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any()) }

    val visitOrders = visitOrderRepository.findAll()

    assertThat(visitOrders.size).isEqualTo(26)

    // Prisoner1 should have 26 VOs
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, 2)
    assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.ACCUMULATED, 24)

    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateStartTimestamp(any(), any(), any())
    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any())
    verify(snsService, times(0)).sendPrisonAllocationAdjustmentCreatedEvent(any())

    val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
    assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 1, processedPrisoners = 0, failedOrSkippedPrisoners = 1)
  }

  /**
   * Scenario - Allocation: Visit allocation job is run, but call to get convicted prisoners fail.
   */
  @Test
  fun `when call to get convicted prisoners fails with a status of INTERNAL_SERVER_ERROR then end time and failure message are populated`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // When
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, null, HttpStatus.INTERNAL_SERVER_ERROR)
    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
      await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
      val visitOrders = visitOrderRepository.findAll()

      assertThat(visitOrders.size).isEqualTo(0)
      val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
      assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], failureMessage = "failed to get convicted prisoners by prisonId - $PRISON_CODE", convictedPrisoners = null, processedPrisoners = null, failedOrSkippedPrisoners = null)
    }
  }

  /**
   * Scenario - Allocation: Visit allocation job is run, but call to get convicted prisoners fail.
   */
  @Test
  fun `when call to get incentive levels for a prison fails with a status of NOT_FOUND then end time and failure message are populated`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)
    incentivesMockServer.stubGetAllPrisonIncentiveLevels(PRISON_CODE, null, HttpStatus.NOT_FOUND)
    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)
    Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
      await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
      val visitOrders = visitOrderRepository.findAll()

      assertThat(visitOrders.size).isEqualTo(0)
      val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
      assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], failureMessage = "failed to get incentive levels by prisonId - $PRISON_CODE", convictedPrisoners = null, processedPrisoners = null, failedOrSkippedPrisoners = null)
    }
  }

  /**
   * Scenario - Allocation: Visit allocation job is run, but call to get convicted prisoners fail.
   */
  @Test
  fun `when call to get incentive levels for a prison fails with a status of INTERNAL_SERVER_ERROR then end time and failure message are populated`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)
    incentivesMockServer.stubGetAllPrisonIncentiveLevels(PRISON_CODE, null, HttpStatus.NOT_FOUND)
    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
      await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
      val visitOrders = visitOrderRepository.findAll()

      assertThat(visitOrders.size).isEqualTo(0)
      val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
      assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], failureMessage = "failed to get incentive levels by prisonId - $PRISON_CODE", convictedPrisoners = null, processedPrisoners = null, failedOrSkippedPrisoners = null)
    }
  }

  /**
   * Scenario - No changes: Visit allocation job is run, but no prisoners are due any changes.
   */
  @Test
  fun `when visit allocation job run for a prison then processMessage is called but prisoner isn't due any changes, no change log is generated`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = prisoner1.prisonerId, LocalDate.now().minusDays(1), LocalDate.now().minusDays(1)))
    entityHelper.createAndSaveVisitOrders(prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(1).atStartOfDay(), 2)
    entityHelper.createAndSaveNegativeVisitOrders(prisoner1.prisonerId, VisitOrderType.VO, 2)

    // When
    val convictedPrisoners = listOf(prisoner1)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any()) }

    val changeLogs = changeLogRepository.findAllByPrisonerPrisonerId(prisoner1.prisonerId)
    assertThat(changeLogs).isNullOrEmpty()

    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateStartTimestamp(any(), any(), any())
    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any())
    verifyNoInteractions(snsService)
    val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
    assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 1, processedPrisoners = 0, failedOrSkippedPrisoners = 1)
  }

  private fun assertVisitOrdersAssignedBy(visitOrders: List<VisitOrder>, prisonerId: String, type: VisitOrderType, status: VisitOrderStatus, total: Int) {
    assertThat(visitOrders.count { it.prisoner.prisonerId == prisonerId && it.type == type && it.status == status }).isEqualTo(total)
  }

  private fun assertNegativeVisitOrdersAssignedBy(visitOrders: List<NegativeVisitOrder>, prisonerId: String, type: VisitOrderType, status: NegativeVisitOrderStatus, total: Int) {
    assertThat(visitOrders.count { it.prisoner.prisonerId == prisonerId && it.type == type && it.status == status }).isEqualTo(total)
  }

  /**
   * Scenario - 205 prisoners, all with Standard (STD) incentive.
   */
  @Test
  fun `when visit allocation job run for a prison and number of prisoners are more than page capacity of 100 then processMessage is called and visit orders are created for convicted prisoners`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))
    val convictedPrisoners = mutableListOf<PrisonerDto>()

    // When
    // there are 205 convicted prisoners with STD incentive - while page limit on prisoner search is 100
    for (i in 1..205) {
      val prisoner = PrisonerDto(prisonerId = "ABC$i", prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = "HEI")
      convictedPrisoners.add(prisoner)
      prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner.prisonerId, createPrisonerDto(prisonerId = prisoner.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
      incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    }

    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any()) }

    val visitOrders = visitOrderRepository.findAll()

    assertThat(visitOrders.size).isEqualTo(205)

    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateStartTimestamp(any(), any(), any())
    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestampAndStats(any(), any(), any(), any(), any(), any())
    verify(snsService, times(205)).sendPrisonAllocationAdjustmentCreatedEvent(any())
    val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
    assertVisitOrderAllocationPrisonJob(visitOrderAllocationPrisonJobs[0], null, convictedPrisoners = 205, processedPrisoners = 205, failedOrSkippedPrisoners = 0)
  }

  private fun assertVisitOrderAllocationPrisonJob(
    visitOrderAllocationPrisonJob: VisitOrderAllocationPrisonJob,
    failureMessage: String?,
    convictedPrisoners: Int?,
    processedPrisoners: Int?,
    failedOrSkippedPrisoners: Int?,
  ) {
    assertThat(visitOrderAllocationPrisonJob.startTimestamp).isNotNull()
    assertThat(visitOrderAllocationPrisonJob.failureMessage).isEqualTo(failureMessage)
    assertThat(visitOrderAllocationPrisonJob.convictedPrisoners).isEqualTo(convictedPrisoners)
    assertThat(visitOrderAllocationPrisonJob.processedPrisoners).isEqualTo(processedPrisoners)
    assertThat(visitOrderAllocationPrisonJob.failedOrSkippedPrisoners).isEqualTo(failedOrSkippedPrisoners)
    assertThat(visitOrderAllocationPrisonJob.endTimestamp).isNotNull()
  }
}
