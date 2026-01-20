package uk.gov.justice.digital.hmpps.visitallocationapi.integration.events

import org.assertj.core.api.Assertions.assertThat
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
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryAttributeType.INCENTIVE_LEVEL
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.TestObjectMapper
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.IncentivesMockExtension.Companion.incentivesMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationPrisonJob
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationEventJob
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime

class AllocationVisitOrderHistoryTest : EventsIntegrationTestBase() {
  companion object {
    const val PRISON_CODE = "MDI"
    val prisoner1 = PrisonerDto(prisonerId = "ABC121", prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = "HEI")
    val prisoner2 = PrisonerDto(prisonerId = "ABC122", prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = "HEI")
  }

  @Test
  fun `when visit allocation job run for a prison then visit order history is created for accumulation, allocation and expiration`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = TestObjectMapper.mapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    val prisoner1Details = PrisonerDetails(prisonerId = prisoner1.prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(14), LocalDate.now().minusDays(28))
    val prisoner2Details = PrisonerDetails(prisonerId = prisoner2.prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(14), LocalDate.now().minusDays(28))

    // 2 visit orders older than 28 days - need to be accumulated
    prisoner1Details.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 33, prisoner1Details, createdTimeStamp = LocalDateTime.now().minusDays(29)))
    prisoner1Details.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner1Details))

    // prisoner 2 has 12 PVOs not older than 28 days
    prisoner2Details.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 12, prisoner2Details, createdTimeStamp = LocalDateTime.now().minusDays(14)))

    // prisoner 2 has 10 PVOs older than 28 days
    prisoner2Details.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 10, prisoner2Details, createdTimeStamp = LocalDateTime.now().minusDays(29)))
    prisonerDetailsRepository.saveAndFlush(prisoner1Details)
    prisonerDetailsRepository.saveAndFlush(prisoner2Details)

    // When
    val convictedPrisoners = listOf(prisoner1, prisoner2)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner2.prisonerId, createPrisonerDto(prisonerId = prisoner2.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner2.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("ENH"))

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

    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(5)
    assertVisitOrderHistory(visitOrderHistoryList[0], prisonerId = prisoner1.prisonerId, comment = null, voBalance = 33, pvoBalance = 1, userName = "SYSTEM", type = VisitOrderHistoryType.VO_ACCUMULATION, attributes = emptyMap())
    assertVisitOrderHistory(visitOrderHistoryList[1], prisonerId = prisoner1.prisonerId, comment = null, voBalance = 25, pvoBalance = 1, userName = "SYSTEM", type = VisitOrderHistoryType.VO_EXPIRATION, attributes = emptyMap())
    assertVisitOrderHistory(visitOrderHistoryList[2], prisonerId = prisoner1.prisonerId, comment = null, voBalance = 26, pvoBalance = 1, userName = "SYSTEM", type = VisitOrderHistoryType.VO_ALLOCATION, attributes = mapOf(INCENTIVE_LEVEL to "STD"))
    assertVisitOrderHistory(visitOrderHistoryList[3], prisonerId = prisoner2.prisonerId, comment = null, voBalance = 2, pvoBalance = 23, userName = "SYSTEM", type = VisitOrderHistoryType.VO_AND_PVO_ALLOCATION, attributes = mapOf(INCENTIVE_LEVEL to "ENH"))
    assertVisitOrderHistory(visitOrderHistoryList[4], prisonerId = prisoner2.prisonerId, comment = null, voBalance = 2, pvoBalance = 13, userName = "SYSTEM", type = VisitOrderHistoryType.PVO_EXPIRATION, attributes = emptyMap())
  }

  @Test
  fun `when visit allocation job run for a prisoner with -ve VOs then visit order history is created for allocation`() {
    // Given - message sent to start allocation job for prison
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationEventJobQueueUrl)
    val allocationJobReference = "job-ref"
    val event = VisitAllocationEventJob(allocationJobReference, PRISON_CODE)
    val message = TestObjectMapper.mapper.writeValueAsString(event)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()
    visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJobReference = allocationJobReference, prisonCode = PRISON_CODE))

    val prisoner1Details = PrisonerDetails(prisonerId = prisoner1.prisonerId, lastVoAllocatedDate = LocalDate.now().minusDays(14), LocalDate.now().minusDays(28))

    // prisoner has 2 -ve visit orders so allocation visit order history entry should still be added
    prisoner1Details.negativeVisitOrders.addAll(createNegativeVisitOrders(VisitOrderType.VO, 1, prisoner1Details))
    prisoner1Details.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner1Details))
    prisonerDetailsRepository.saveAndFlush(prisoner1Details)

    // When
    val convictedPrisoners = listOf(prisoner1)
    prisonerSearchMockServer.stubGetConvictedPrisoners(PRISON_CODE, convictedPrisoners)

    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisoner1.prisonerId, createPrisonerDto(prisonerId = prisoner1.prisonerId, prisonId = PRISON_CODE, inOutStatus = "IN", lastPrisonId = PRISON_CODE))

    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory(prisoner1.prisonerId, prisonerIncentivesDto = PrisonerIncentivesDto("STD"))

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

    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(1)
    assertVisitOrderHistory(visitOrderHistoryList[0], prisonerId = prisoner1.prisonerId, comment = null, voBalance = 0, pvoBalance = 1, userName = "SYSTEM", type = VisitOrderHistoryType.VO_ALLOCATION, attributes = mapOf(INCENTIVE_LEVEL to "STD"))
  }
}
