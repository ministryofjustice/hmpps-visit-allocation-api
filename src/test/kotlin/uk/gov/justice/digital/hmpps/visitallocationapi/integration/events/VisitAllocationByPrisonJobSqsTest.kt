package uk.gov.justice.digital.hmpps.visitallocationapi.integration.events

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonerIncentivesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.IncentivesMockExtension.Companion.incentivesMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationPrisonJob
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJob
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class VisitAllocationByPrisonJobSqsTest : EventsIntegrationTestBase() {
  @BeforeEach
  fun setup() {
    visitOrderAllocationPrisonJobRepository.deleteAll()
    visitOrderRepository.deleteAll()
  }

  @AfterEach
  fun cleanUp() {
    visitOrderAllocationPrisonJobRepository.deleteAll()
    visitOrderRepository.deleteAll()
  }

  companion object {
    const val PRISON_CODE = "MDI"
    val prisoner1 = PrisonerDto(prisonerId = "ABC121", prisonId = PRISON_CODE)
    val prisoner2 = PrisonerDto(prisonerId = "ABC122", prisonId = PRISON_CODE)
    val prisoner3 = PrisonerDto(prisonerId = "ABC123", prisonId = PRISON_CODE)
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
    await untilAsserted { verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestamp(any(), any(), any()) }

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
    verify(visitOrderAllocationPrisonJobRepository, times(1)).updateEndTimestamp(any(), any(), any())
    val visitOrderAllocationPrisonJobs = visitOrderAllocationPrisonJobRepository.findAll()
    assertThat(visitOrderAllocationPrisonJobs[0].startTimestamp).isNotNull()
    assertThat(visitOrderAllocationPrisonJobs[0].endTimestamp).isNotNull()
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
    val existingVOs = mutableListOf<VisitOrder>().apply {
      addAll(List(2) { createVisitOrder(prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(29)) })
      addAll(List(2) { createVisitOrder(prisoner3.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(14)) })
    }
    visitOrderRepository.saveAll(existingVOs)

    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJObjectReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJObjectReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)
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
      .atMost(5, TimeUnit.SECONDS)
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
    val existingVOs = mutableListOf<VisitOrder>().apply {
      addAll(List(28) { createVisitOrder(prisoner2.prisonerId, VisitOrderType.VO, VisitOrderStatus.ACCUMULATED, LocalDate.now().minusDays(1)) })
      addAll(List(2) { createVisitOrder(prisoner3.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(29)) })
    }
    visitOrderRepository.saveAll(existingVOs)

    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJObjectReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJObjectReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)
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
      .atMost(5, TimeUnit.SECONDS)
      .untilAsserted {
        // Then
        await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
        await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
        await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
        val visitOrders = visitOrderRepository.findAll()

        assertThat(visitOrders.size).isEqualTo(36)

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
      }
  }

  private fun assertVisitOrdersAssignedBy(visitOrders: List<VisitOrder>, prisonerId: String, type: VisitOrderType, status: VisitOrderStatus, total: Int) {
    assertThat(visitOrders.count { it.prisonerId == prisonerId && it.type == type && it.status == status }).isEqualTo(total)
  }

  private fun createVisitOrder(prisonerId: String, type: VisitOrderType, status: VisitOrderStatus, createdDate: LocalDate): VisitOrder {
    return VisitOrder(prisonerId = prisonerId, type = type, status = status, createdDate = createdDate)
  }
}
