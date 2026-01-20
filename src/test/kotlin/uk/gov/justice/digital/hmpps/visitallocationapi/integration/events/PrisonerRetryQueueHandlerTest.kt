package uk.gov.justice.digital.hmpps.visitallocationapi.integration.events

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonerIncentivesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryAttributeType.INCENTIVE_LEVEL
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.TestObjectMapper
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.IncentivesMockExtension.Companion.incentivesMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.VisitAllocationPrisonerRetryQueueListener
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationPrisonerRetrySqsService.VisitAllocationPrisonerRetryJob
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

class PrisonerRetryQueueHandlerTest : EventsIntegrationTestBase() {
  @MockitoSpyBean
  lateinit var visitAllocationPrisonerRetryQueueListenerSpy: VisitAllocationPrisonerRetryQueueListener

  @Test
  fun `when prisoner put on retry queue the message is processed`() {
    // Given
    val prisonerId = "TEST"

    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationPrisonerRetryQueueUrl)
    val visitAllocationPrisonerRetryJob = VisitAllocationPrisonerRetryJob("job-reference", prisonerId)
    val message = TestObjectMapper.mapper.writeValueAsString(visitAllocationPrisonerRetryJob)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    prisonVisitsAllocationPrisonerRetryQueueSqsClient.sendMessage(sendMessageRequest)
    prisonerSearchMockServer.stubGetPrisonerById("TEST", prisoner = createPrisonerDto(prisonerId = prisonerId, prisonId = "HEI", inOutStatus = "IN"))
    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = "HEI",
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory("TEST", prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonIncentiveLevelsByLevelCode(prisonId = "HEI", levelCode = "STD", prisonIncentiveAmountsDto = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))

    // Then
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueSqsClient.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueUrl).get() } matches { it == 0 }
    await untilAsserted { verify(visitAllocationPrisonerRetryQueueListenerSpy, times(1)).processMessage(visitAllocationPrisonerRetryJob) }
    await untilAsserted { verify(prisonerDetailsRepository, times(1)).insertNewPrisonerDetails(any(), any(), eq(null)) }
    await untilAsserted { verify(snsService, times(1)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    val visitOrders = visitOrderRepository.findAll()
    Assertions.assertThat(visitOrders.size).isEqualTo(1) //  STD level = 1 VO & 0 PVO

    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(1)
    assertVisitOrderHistory(visitOrderHistoryList[0], prisonerId = prisonerId, comment = null, voBalance = 1, pvoBalance = 0, userName = "SYSTEM", type = VisitOrderHistoryType.VO_ALLOCATION, attributes = mapOf(INCENTIVE_LEVEL to "STD"))
  }

  @Test
  fun `when prisoner put on retry queue but a 404 is returned from prisoner search the message is sent to DLQ`() {
    // Given
    val prisonerId = "TEST"
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationPrisonerRetryQueueUrl)
    val visitAllocationPrisonerRetryJob = VisitAllocationPrisonerRetryJob("job-reference", prisonerId)
    val message = TestObjectMapper.mapper.writeValueAsString(visitAllocationPrisonerRetryJob)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    prisonVisitsAllocationPrisonerRetryQueueSqsClient.sendMessage(sendMessageRequest)
    prisonerSearchMockServer.stubGetPrisonerById("TEST", null, HttpStatus.NOT_FOUND)
    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = "HEI",
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory("TEST", prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonIncentiveLevelsByLevelCode(prisonId = "HEI", levelCode = "STD", prisonIncentiveAmountsDto = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))

    // Then
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueSqsClient.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueUrl).get() } matches { it == 0 }
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueDlqClient!!.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueDlqUrl!!).get() } matches { it == 1 }

    await untilAsserted { verify(visitOrderRepository, times(0)).saveAll(any<List<VisitOrder>>()) }
    val visitOrders = visitOrderRepository.findAll()
    Assertions.assertThat(visitOrders.size).isEqualTo(0)

    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(0)
  }

  @Test
  fun `when prisoner put on retry queue but a 500 is returned from prisoner search the message is sent to DLQ`() {
    // Given
    val prisonerId = "TEST"
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationPrisonerRetryQueueUrl)
    val visitAllocationPrisonerRetryJob = VisitAllocationPrisonerRetryJob("job-reference", prisonerId)
    val message = TestObjectMapper.mapper.writeValueAsString(visitAllocationPrisonerRetryJob)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    prisonVisitsAllocationPrisonerRetryQueueSqsClient.sendMessage(sendMessageRequest)
    prisonerSearchMockServer.stubGetPrisonerById("TEST", null, HttpStatus.INTERNAL_SERVER_ERROR)
    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = "HEI",
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory("TEST", prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonIncentiveLevelsByLevelCode(prisonId = "HEI", levelCode = "STD", prisonIncentiveAmountsDto = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))

    // Then
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueSqsClient.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueUrl).get() } matches { it == 0 }
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueDlqClient!!.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueDlqUrl!!).get() } matches { it == 1 }

    await untilAsserted { verify(visitOrderRepository, times(0)).saveAll(any<List<VisitOrder>>()) }
    val visitOrders = visitOrderRepository.findAll()
    Assertions.assertThat(visitOrders.size).isEqualTo(0)

    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(0)
  }

  @Test
  fun `when prisoner put on retry queue but a 404 is returned from prisoner incentive review history the message is sent to DLQ`() {
    // Given
    val prisonerId = "TEST"
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationPrisonerRetryQueueUrl)
    val visitAllocationPrisonerRetryJob = VisitAllocationPrisonerRetryJob("job-reference", prisonerId)
    val message = TestObjectMapper.mapper.writeValueAsString(visitAllocationPrisonerRetryJob)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    prisonVisitsAllocationPrisonerRetryQueueSqsClient.sendMessage(sendMessageRequest)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = "HEI", inOutStatus = "IN"))
    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = "HEI",
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory("TEST", null, HttpStatus.NOT_FOUND)
    incentivesMockServer.stubGetPrisonIncentiveLevelsByLevelCode(prisonId = "HEI", levelCode = "STD", prisonIncentiveAmountsDto = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))

    // Then
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueSqsClient.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueUrl).get() } matches { it == 0 }
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueDlqClient!!.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueDlqUrl!!).get() } matches { it == 1 }

    await untilAsserted { verify(visitOrderRepository, times(0)).saveAll(any<List<VisitOrder>>()) }
    val visitOrders = visitOrderRepository.findAll()
    Assertions.assertThat(visitOrders.size).isEqualTo(0)

    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(0)
  }

  @Test
  fun `when prisoner put on retry queue but a 500 is returned from prisoner incentive review history the message is sent to DLQ`() {
    // Given
    val prisonerId = "TEST"
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationPrisonerRetryQueueUrl)
    val visitAllocationPrisonerRetryJob = VisitAllocationPrisonerRetryJob("job-reference", prisonerId)
    val message = TestObjectMapper.mapper.writeValueAsString(visitAllocationPrisonerRetryJob)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    prisonVisitsAllocationPrisonerRetryQueueSqsClient.sendMessage(sendMessageRequest)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = "HEI", inOutStatus = "IN"))
    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = "HEI",
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 1, privilegedVisitOrders = 0, levelCode = "STD"),
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory("TEST", null, HttpStatus.INTERNAL_SERVER_ERROR)
    incentivesMockServer.stubGetPrisonIncentiveLevelsByLevelCode(prisonId = "HEI", levelCode = "STD", prisonIncentiveAmountsDto = PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "STD"))

    // Then
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueSqsClient.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueUrl).get() } matches { it == 0 }
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueDlqClient!!.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueDlqUrl!!).get() } matches { it == 1 }

    await untilAsserted { verify(visitOrderRepository, times(0)).saveAll(any<List<VisitOrder>>()) }
    val visitOrders = visitOrderRepository.findAll()
    Assertions.assertThat(visitOrders.size).isEqualTo(0)

    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(0)
  }

  @Test
  fun `when prisoner put on retry queue but a 404 is returned from get prisoner incentive levels the message is sent to DLQ`() {
    // Given
    val prisonerId = "TEST"
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationPrisonerRetryQueueUrl)
    val visitAllocationPrisonerRetryJob = VisitAllocationPrisonerRetryJob("job-reference", prisonerId)
    val message = TestObjectMapper.mapper.writeValueAsString(visitAllocationPrisonerRetryJob)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    prisonVisitsAllocationPrisonerRetryQueueSqsClient.sendMessage(sendMessageRequest)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = "HEI", inOutStatus = "IN"))
    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = "HEI",
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory("TEST", prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonIncentiveLevelsByLevelCode(prisonId = "HEI", levelCode = "STD", null, HttpStatus.NOT_FOUND)

    // Then
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueSqsClient.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueUrl).get() } matches { it == 0 }
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueDlqClient!!.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueDlqUrl!!).get() } matches { it == 1 }

    await untilAsserted { verify(visitOrderRepository, times(0)).saveAll(any<List<VisitOrder>>()) }
    val visitOrders = visitOrderRepository.findAll()
    Assertions.assertThat(visitOrders.size).isEqualTo(0)

    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(0)
  }

  @Test
  fun `when prisoner put on retry queue but a 500 is returned from get prisoner incentive levels the message is sent to DLQ`() {
    // Given
    val prisonerId = "TEST"
    val sendMessageRequestBuilder = SendMessageRequest.builder().queueUrl(prisonVisitsAllocationPrisonerRetryQueueUrl)
    val visitAllocationPrisonerRetryJob = VisitAllocationPrisonerRetryJob("job-reference", prisonerId)
    val message = TestObjectMapper.mapper.writeValueAsString(visitAllocationPrisonerRetryJob)
    val sendMessageRequest = sendMessageRequestBuilder.messageBody(message).build()

    // When
    prisonVisitsAllocationPrisonerRetryQueueSqsClient.sendMessage(sendMessageRequest)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = "HEI", inOutStatus = "IN"))
    incentivesMockServer.stubGetAllPrisonIncentiveLevels(
      prisonId = "HEI",
      listOf(
        PrisonIncentiveAmountsDto(visitOrders = 2, privilegedVisitOrders = 1, levelCode = "ENH"),
        PrisonIncentiveAmountsDto(visitOrders = 3, privilegedVisitOrders = 2, levelCode = "ENH2"),
      ),
    )
    incentivesMockServer.stubGetPrisonerIncentiveReviewHistory("TEST", prisonerIncentivesDto = PrisonerIncentivesDto("STD"))
    incentivesMockServer.stubGetPrisonIncentiveLevelsByLevelCode(prisonId = "HEI", levelCode = "STD", null, HttpStatus.INTERNAL_SERVER_ERROR)

    // Then
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueSqsClient.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueUrl).get() } matches { it == 0 }
    await untilCallTo { prisonVisitsAllocationPrisonerRetryQueueDlqClient!!.countMessagesOnQueue(prisonVisitsAllocationPrisonerRetryQueueDlqUrl!!).get() } matches { it == 1 }

    await untilAsserted { verify(visitOrderRepository, times(0)).saveAll(any<List<VisitOrder>>()) }
    val visitOrders = visitOrderRepository.findAll()
    Assertions.assertThat(visitOrders.size).isEqualTo(0)

    val visitOrderHistoryList = visitOrderHistoryRepository.findAll()
    assertThat(visitOrderHistoryList.size).isEqualTo(0)
  }
}
