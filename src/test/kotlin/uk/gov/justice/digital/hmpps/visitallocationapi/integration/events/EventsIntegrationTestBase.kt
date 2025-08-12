package uk.gov.justice.digital.hmpps.visitallocationapi.integration.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.VisitDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.events.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.EntityHelper
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.IncentivesMockExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.VisitSchedulerMockExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.ChangeLogRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationPrisonJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.DomainEventListenerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.NomisSyncService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ProcessPrisonerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.SnsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener.Companion.PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.VisitAllocationByPrisonJobListener
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.VisitAllocationByPrisonJobListener.Companion.PRISON_VISITS_ALLOCATION_EVENT_JOB_QUEUE_CONFIG_KEY
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.VisitAllocationPrisonerRetryQueueListener.Companion.PRISON_VISITS_ALLOCATION_PRISONER_RETRY_QUEUE_CONFIG_KEY
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationPrisonerRetrySqsService
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(
  HmppsAuthApiExtension::class,
  PrisonerSearchMockExtension::class,
  IncentivesMockExtension::class,
  PrisonApiMockExtension::class,
  VisitSchedulerMockExtension::class,
)
abstract class EventsIntegrationTestBase {
  companion object {
    private val localStackContainer = LocalStackContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun testcontainers(registry: DynamicPropertyRegistry) {
      localStackContainer?.also { setLocalStackProperties(it, registry) }
    }
  }

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  @Autowired
  protected lateinit var entityHelper: EntityHelper

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  internal val topic by lazy { hmppsQueueService.findByTopicId("domainevents") as HmppsTopic }

  internal val prisonVisitsAllocationsQueue by lazy { hmppsQueueService.findByQueueId(PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY) as HmppsQueue }
  internal val prisonVisitsAllocationEventJobQueue by lazy { hmppsQueueService.findByQueueId(PRISON_VISITS_ALLOCATION_EVENT_JOB_QUEUE_CONFIG_KEY) as HmppsQueue }
  internal val prisonVisitsAllocationPrisonerRetryQueue by lazy { hmppsQueueService.findByQueueId(PRISON_VISITS_ALLOCATION_PRISONER_RETRY_QUEUE_CONFIG_KEY) as HmppsQueue }

  internal val domainEventsSqsClient by lazy { prisonVisitsAllocationsQueue.sqsClient }
  internal val domainEventsSqsDlqClient by lazy { prisonVisitsAllocationsQueue.sqsDlqClient }
  internal val domainEventsQueueUrl by lazy { prisonVisitsAllocationsQueue.queueUrl }
  internal val domainEventsDlqUrl by lazy { prisonVisitsAllocationsQueue.dlqUrl }
  internal val prisonVisitsAllocationEventJobSqsClient by lazy { prisonVisitsAllocationEventJobQueue.sqsClient }
  internal val prisonVisitsAllocationEventJobQueueUrl by lazy { prisonVisitsAllocationEventJobQueue.queueUrl }
  internal val prisonVisitsAllocationEventJobSqsDlqClient by lazy { prisonVisitsAllocationEventJobQueue.sqsDlqClient }
  internal val prisonVisitsAllocationEventJobDlqUrl by lazy { prisonVisitsAllocationEventJobQueue.dlqUrl }
  internal val prisonVisitsAllocationPrisonerRetryQueueSqsClient by lazy { prisonVisitsAllocationPrisonerRetryQueue.sqsClient }
  internal val prisonVisitsAllocationPrisonerRetryQueueUrl by lazy { prisonVisitsAllocationPrisonerRetryQueue.queueUrl }
  internal val prisonVisitsAllocationPrisonerRetryQueueDlqClient by lazy { prisonVisitsAllocationPrisonerRetryQueue.sqsDlqClient }
  internal val prisonVisitsAllocationPrisonerRetryQueueDlqUrl by lazy { prisonVisitsAllocationPrisonerRetryQueue.dlqUrl }

  internal val awsSnsClient by lazy { topic.snsClient }
  internal val topicArn by lazy { topic.arn }

  @MockitoSpyBean
  lateinit var domainEventListenerSpy: DomainEventListener

  @MockitoSpyBean
  lateinit var domainEventListenerServiceSpy: DomainEventListenerService

  @MockitoSpyBean
  lateinit var visitOrderRepository: VisitOrderRepository

  @MockitoSpyBean
  lateinit var prisonerDetailsRepository: PrisonerDetailsRepository

  @MockitoSpyBean
  lateinit var visitOrderAllocationPrisonJobRepository: VisitOrderAllocationPrisonJobRepository

  @MockitoSpyBean
  lateinit var visitAllocationByPrisonJobListenerSpy: VisitAllocationByPrisonJobListener

  @MockitoSpyBean
  lateinit var visitAllocationPrisonerRetrySqsService: VisitAllocationPrisonerRetrySqsService

  @MockitoSpyBean
  lateinit var negativeVisitOrderRepository: NegativeVisitOrderRepository

  @MockitoSpyBean
  lateinit var nomisSyncService: NomisSyncService

  @MockitoSpyBean
  lateinit var processPrisonerService: ProcessPrisonerService

  @MockitoSpyBean
  lateinit var changeLogService: ChangeLogService

  @MockitoSpyBean
  lateinit var changeLogRepository: ChangeLogRepository

  @MockitoSpyBean
  lateinit var snsService: SnsService

  @MockitoSpyBean
  lateinit var telemetryClient: TelemetryClient

  @AfterEach
  @BeforeEach
  fun cleanQueue() {
    purgeQueue(domainEventsSqsClient, domainEventsQueueUrl)
    purgeQueue(domainEventsSqsDlqClient!!, domainEventsDlqUrl!!)
    purgeQueue(prisonVisitsAllocationEventJobSqsClient, prisonVisitsAllocationEventJobQueueUrl)
    purgeQueue(prisonVisitsAllocationEventJobSqsDlqClient!!, prisonVisitsAllocationEventJobDlqUrl!!)
    purgeQueue(prisonVisitsAllocationPrisonerRetryQueueSqsClient, prisonVisitsAllocationPrisonerRetryQueueUrl)
    purgeQueue(prisonVisitsAllocationPrisonerRetryQueueDlqClient!!, prisonVisitsAllocationPrisonerRetryQueueDlqUrl!!)
  }

  @BeforeEach
  fun setup() {
    visitOrderAllocationPrisonJobRepository.deleteAll()
    visitOrderRepository.deleteAll()
    negativeVisitOrderRepository.deleteAll()
    prisonerDetailsRepository.deleteAll()
  }

  @AfterEach
  fun cleanUp() {
    visitOrderAllocationPrisonJobRepository.deleteAll()
    visitOrderRepository.deleteAll()
    negativeVisitOrderRepository.deleteAll()
    prisonerDetailsRepository.deleteAll()
  }

  fun purgeQueue(client: SqsAsyncClient, url: String) {
    client.purgeQueue(PurgeQueueRequest.builder().queueUrl(url).build()).get()
  }

  fun createDomainEventPublishRequest(eventType: String, domainEvent: String): PublishRequest? = PublishRequest.builder()
    .topicArn(topicArn)
    .message(domainEvent).build()

  fun createDomainEventJson(eventType: String, additionalInformation: String): String = "{\"eventType\":\"$eventType\",\"additionalInformation\":$additionalInformation}"

  fun createPrisonerConvictionStatusChangedAdditionalInformationJson(prisonerId: String, convictedStatus: String): String {
    val jsonValues = HashMap<String, String>()

    jsonValues["nomsNumber"] = prisonerId
    jsonValues["convictedStatus"] = convictedStatus

    return createAdditionalInformationJson(jsonValues)
  }

  fun createPrisonerMergedAdditionalInformationJson(prisonerId: String, removedPrisonerId: String): String {
    val jsonValues = HashMap<String, String>()

    jsonValues["nomsNumber"] = prisonerId
    jsonValues["removedNomsNumber"] = removedPrisonerId

    return createAdditionalInformationJson(jsonValues)
  }

  fun createPrisonerBookingMovedAdditionalInformationJson(movedFromPrisonerId: String, movedToPrisonerId: String): String {
    val jsonValues = HashMap<String, String>()

    jsonValues["movedFromNomsNumber"] = movedFromPrisonerId
    jsonValues["movedToNomsNumber"] = movedToPrisonerId

    return createAdditionalInformationJson(jsonValues)
  }

  fun createVisitBookedAdditionalInformationJson(visitReference: String): String {
    val jsonValues = HashMap<String, String>()

    jsonValues["reference"] = visitReference

    return createAdditionalInformationJson(jsonValues)
  }

  fun createPrisonerReceivedAdditionalInformationJson(prisonerId: String, prisonId: String, reason: PrisonerReceivedReasonType): String {
    val jsonValues = HashMap<String, String>()

    jsonValues["nomsNumber"] = prisonerId
    jsonValues["prisonId"] = prisonId
    jsonValues["reason"] = reason.name

    return createAdditionalInformationJson(jsonValues)
  }

  protected fun createPrisonerDto(prisonerId: String, prisonId: String = "MDI", inOutStatus: String = "IN", lastPrisonId: String = "HEI", convictedStatus: String? = "Convicted"): PrisonerDto = PrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = inOutStatus, lastPrisonId = lastPrisonId, convictedStatus = convictedStatus)

  protected fun createVisitBalancesDto(remainingVo: Int, remainingPvo: Int, latestIepAdjustDate: LocalDate? = null, latestPrivIepAdjustDate: LocalDate? = null): VisitBalancesDto = VisitBalancesDto(remainingVo, remainingPvo, latestIepAdjustDate, latestPrivIepAdjustDate)

  protected fun createVisitDto(visitReference: String, prisonerId: String, prisonId: String): VisitDto = VisitDto(visitReference, prisonerId, prisonId)

  private fun createAdditionalInformationJson(jsonValues: Map<String, Any>): String {
    val builder = StringBuilder()
    builder.append("{")
    jsonValues.entries.forEachIndexed { index, entry ->
      builder.append(getJsonString(entry))

      if (index < jsonValues.size - 1) {
        builder.append(",")
      }
    }
    builder.append("}")
    return builder.toString()
  }

  private fun getJsonString(entry: Map.Entry<String, Any>): String = when (entry.value) {
    is List<*> -> {
      ("\"${entry.key}\":[${(entry.value as List<*>).joinToString { "\"" + it + "\"" }}]")
    }

    is Number -> {
      ("\"${entry.key}\":${entry.value}")
    }

    else -> {
      ("\"${entry.key}\":\"${entry.value}\"")
    }
  }

  protected fun createVisitOrders(visitOrderType: VisitOrderType, amountToCreate: Int, prisoner: PrisonerDetails): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(amountToCreate) {
      visitOrders.add(
        VisitOrder(
          type = visitOrderType,
          status = VisitOrderStatus.AVAILABLE,
          prisoner = prisoner,
        ),
      )
    }
    return visitOrders
  }

  protected fun createNegativeVisitOrders(visitOrderType: VisitOrderType, amountToCreate: Int, prisoner: PrisonerDetails): List<NegativeVisitOrder> {
    val negativeVisitOrder = mutableListOf<NegativeVisitOrder>()
    repeat(amountToCreate) {
      negativeVisitOrder.add(
        NegativeVisitOrder(
          type = visitOrderType,
          status = NegativeVisitOrderStatus.USED,
          prisoner = prisoner,
        ),
      )
    }
    return negativeVisitOrder
  }
}
