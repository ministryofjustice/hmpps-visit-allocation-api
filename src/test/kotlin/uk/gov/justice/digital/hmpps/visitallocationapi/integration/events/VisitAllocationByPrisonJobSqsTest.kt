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
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJob
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Scenario: Visit allocation job is run, and all prisoners are processed for visit order allocations (VO / PVO).
 * Prisoner1 - Standard incentive, Gets 1 VO, 0 PVO. Has no existing VOs, so no accumulation / expiry occurs.
 * Prisoner2 - Enhanced incentive, Gets 2 VO, 1 PVO. Has no existing VOs, so no accumulation / expiry occurs.
 * Prisoner3 - Enhanced2 incentive, Gets 3 VO, 2 PVOs. Has 2 existing VOs older than 28 days, so accumulation occurs but no expiry.
 * Prisoner4 - Enhanced3 incentive, Gets 4 VO, 3 PVOs. Has 27 existing VOs, so expiry occurs for 1 VOs.
 * Prisoner5 - Standard incentive, Gets 1 VO, 0 PVO. Has existing PVOs older than 28 days, so they are expired.
 */

class VisitAllocationByPrisonJobSqsTest : EventsIntegrationTestBase() {
  @BeforeEach
  fun setup() {
    visitOrderRepository.deleteAll()

    val visitOrders = mutableListOf<VisitOrder>().apply {
      addAll(List(2) { createVisitOrder(prisoner3.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(29)) })
      addAll(List(27) { createVisitOrder(prisoner4.prisonerId, VisitOrderType.VO, VisitOrderStatus.ACCUMULATED, LocalDate.now().minusDays(15)) })
      addAll(List(1) { createVisitOrder(prisoner5.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(29)) })
    }

    visitOrderRepository.saveAll(visitOrders)
  }

  @AfterEach
  fun cleanUp() {
    visitOrderRepository.deleteAll()
  }

  companion object {
    const val PRISON_CODE = "MDI"
    val prisoner1 = PrisonerDto(prisonerId = "ABC121", prisonId = PRISON_CODE)
    val prisoner2 = PrisonerDto(prisonerId = "ABC122", prisonId = PRISON_CODE)
    val prisoner3 = PrisonerDto(prisonerId = "ABC123", prisonId = PRISON_CODE)
    val prisoner4 = PrisonerDto(prisonerId = "ABC124", prisonId = PRISON_CODE)
    val prisoner5 = PrisonerDto(prisonerId = "ABC125", prisonId = PRISON_CODE)
  }

  @Test
  fun `when visit allocation job run for a prison then processMessage is called and visit orders are created for convicted prisoners`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val event = VisitAllocationEventJob(PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2, prisoner3, prisoner4, prisoner5)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner2.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner3.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH2"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner4.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH3"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner5.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))

    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = PRISON_CODE,
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
        PrisonIncentiveAmountsDto(visitOrders = 4, privilegedVisitOrders = 3, levelCode = "ENH3"),
      ),
    )

    prisonVisitsAllocationEventJobSqsClient.sendMessage(sendMessageRequest)

    // Then
    Awaitility.await()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted {
        // Then
        await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
        await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
        await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }
        val visitOrders = visitOrderRepository.findAll()

        assertThat(visitOrders.size).isEqualTo(46)

        // as prisoner1 is STD he should only get 1 VO and 0 PVOs
        assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.VO, 1)
        assertVisitOrdersAssignedBy(visitOrders, prisoner1.prisonerId, VisitOrderType.PVO, 0)

        // as prisoner2 is ENH he should get 2 VOs and 1 PVO
        assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.VO, 2)
        assertVisitOrdersAssignedBy(visitOrders, prisoner2.prisonerId, VisitOrderType.PVO, 1)

        // as prisoner3 is ENH2 he should get 3 VOs and 2 PVO (+2 existing expired VOs)
        assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.VO, 5)
        assertVisitOrdersAssignedBy(visitOrders, prisoner3.prisonerId, VisitOrderType.PVO, 2)

        // as prisoner4 is ENH3 he should get 4 VOs and 3 PVO (+27 existing accumulated VOs).
        assertVisitOrdersAssignedBy(visitOrders, prisoner4.prisonerId, VisitOrderType.VO, 31)
        assertVisitOrdersAssignedBy(visitOrders, prisoner4.prisonerId, VisitOrderType.PVO, 3)

        // as prisoner5 is STD he should get 1 VOs and 0 PVO (+1 existing, now expired PVO).
        assertVisitOrdersAssignedBy(visitOrders, prisoner5.prisonerId, VisitOrderType.VO, 1)
        assertVisitOrdersAssignedBy(visitOrders, prisoner5.prisonerId, VisitOrderType.PVO, 1)
      }
  }

  private fun assertVisitOrdersAssignedBy(visitOrders: List<VisitOrder>, prisonerId: String, type: VisitOrderType, total: Int) {
    assertThat(visitOrders.count { it.prisonerId == prisonerId && it.type == type }).isEqualTo(total)
  }

  private fun createVisitOrder(prisonerId: String, type: VisitOrderType, status: VisitOrderStatus, createdDate: LocalDate): VisitOrder {
    return VisitOrder(prisonerId = prisonerId, type = type, status = status, createdDate = createdDate)
  }
}
