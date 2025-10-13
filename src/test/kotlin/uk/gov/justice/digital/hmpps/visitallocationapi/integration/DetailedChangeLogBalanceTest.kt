package uk.gov.justice.digital.hmpps.visitallocationapi.integration

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
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonerIncentivesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.events.EventsIntegrationTestBase
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.IncentivesMockExtension.Companion.incentivesMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationPrisonJob
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJob
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DetailedChangeLogBalanceTest : EventsIntegrationTestBase() {
  companion object {
    const val PRISON_CODE = "MDI"
    val prisoner1 = PrisonerDto(prisonerId = "ABC121", prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = "HEI")
  }

  @Test
  fun `when visit allocation job run for a prison then processMessage is called, then change log detailed balance entries are recorded correctly`() {
    entityHelper.createPrisonerDetails(PrisonerDetails(prisonerId = prisoner1.prisonerId, LocalDate.now().minusDays(15), null))

    entityHelper.createAndSaveVisitOrders(prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(14).atStartOfDay(), 2)
    entityHelper.createAndSaveVisitOrders(prisoner1.prisonerId, VisitOrderType.VO, VisitOrderStatus.ACCUMULATED, LocalDate.now().minusDays(29).atStartOfDay(), 2)
    entityHelper.createAndSaveNegativeVisitOrders(prisoner1.prisonerId, VisitOrderType.VO, 2)

    entityHelper.createAndSaveVisitOrders(prisoner1.prisonerId, VisitOrderType.PVO, VisitOrderStatus.AVAILABLE, LocalDate.now().minusDays(14).atStartOfDay(), 2)
    entityHelper.createAndSaveNegativeVisitOrders(prisoner1.prisonerId, VisitOrderType.PVO, 1)

    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = objectMapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

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

    // Then
    Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted {
        // Then
        await untilCallTo { prisonVisitsAllocationEventJobSqsClient.countMessagesOnQueue(prisonVisitsAllocationEventJobQueueUrl).get() } matches { it == 0 }
        await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(any()) }
        await untilAsserted { verify(visitAllocationByPrisonJobListenerSpy, times(1)).processMessage(event) }

        val changeLogs = changeLogRepository.findAllByPrisonerPrisonerId(prisoner1.prisonerId)!!
        assertThat(changeLogs).hasSize(1)

        val changeLog = changeLogs.first()
        assertThat(changeLog.changeType).isEqualTo(ChangeLogType.BATCH_PROCESS)
        assertThat(changeLog.visitOrderBalance).isEqualTo(3)
        assertThat(changeLog.visitOrderAvailableBalance).isEqualTo(2)
        assertThat(changeLog.visitOrderAccumulatedBalance).isEqualTo(2)
        assertThat(changeLog.visitOrderUsedBalance).isEqualTo(1)
        assertThat(changeLog.privilegedVisitOrderBalance).isEqualTo(1)
        assertThat(changeLog.privilegedVisitOrderAvailableBalance).isEqualTo(2)
        assertThat(changeLog.privilegedVisitOrderUsedBalance).isEqualTo(1)
      }
  }
}
