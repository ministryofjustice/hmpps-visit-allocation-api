package uk.gov.justice.digital.hmpps.visitallocationapi.integration.events

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.events.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.IncentivesMockExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationPrisonJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderPrisonRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.DomainEventListenerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener.Companion.PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.VisitAllocationByPrisonJobListener
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.VisitAllocationByPrisonJobListener.Companion.PRISON_VISITS_ALLOCATION_EVENT_JOB_QUEUE_CONFIG_KEY
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.VisitAllocationPrisonerRetryQueueListener.Companion.PRISON_VISITS_ALLOCATION_PRISONER_RETRY_QUEUE_CONFIG_KEY
import uk.gov.justice.digital.hmpps.visitallocationapi.service.sqs.VisitAllocationPrisonerRetrySqsService
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(
  HmppsAuthApiExtension::class,
  PrisonerSearchMockExtension::class,
  IncentivesMockExtension::class,
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
  lateinit var visitOrderPrisonRepositorySpy: VisitOrderPrisonRepository

  @MockitoSpyBean
  lateinit var visitAllocationByPrisonJobListenerSpy: VisitAllocationByPrisonJobListener

  @MockitoSpyBean
  lateinit var visitAllocationPrisonerRetrySqsService: VisitAllocationPrisonerRetrySqsService

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
  fun clearDB() {
    visitOrderRepository.deleteAll()
    visitOrderPrisonRepositorySpy.deleteAll()
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
}
