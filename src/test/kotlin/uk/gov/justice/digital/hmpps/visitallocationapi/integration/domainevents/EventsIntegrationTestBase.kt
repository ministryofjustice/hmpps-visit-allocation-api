package uk.gov.justice.digital.hmpps.visitallocationapi.integration.domainevents

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
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.domainevents.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener.Companion.PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.processors.PrisonerConvictionStatusUpdatedProcessor
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(HmppsAuthApiExtension::class, PrisonerSearchMockExtension::class)
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

  internal val sqsClient by lazy { prisonVisitsAllocationsQueue.sqsClient }
  internal val sqsDlqClient by lazy { prisonVisitsAllocationsQueue.sqsDlqClient }
  internal val queueUrl by lazy { prisonVisitsAllocationsQueue.queueUrl }
  internal val dlqUrl by lazy { prisonVisitsAllocationsQueue.dlqUrl }

  internal val awsSnsClient by lazy { topic.snsClient }
  internal val topicArn by lazy { topic.arn }

  @MockitoSpyBean
  lateinit var domainEventListenerSpy: DomainEventListener

  @MockitoSpyBean
  lateinit var prisonerConvictionStatusUpdatedProcessorSpy: PrisonerConvictionStatusUpdatedProcessor

  @BeforeEach
  fun cleanQueue() {
    purgeQueue(sqsClient, queueUrl)
    purgeQueue(sqsDlqClient!!, dlqUrl!!)
  }

  fun purgeQueue(client: SqsAsyncClient, url: String) {
    client.purgeQueue(PurgeQueueRequest.builder().queueUrl(url).build()).get()
  }

  fun createDomainEventPublishRequest(eventType: String, domainEvent: String): PublishRequest? {
    return PublishRequest.builder()
      .topicArn(topicArn)
      .message(domainEvent).build()
  }

  fun createDomainEventJson(eventType: String, additionalInformation: String): String {
    return "{\"eventType\":\"$eventType\",\"additionalInformation\":$additionalInformation}"
  }

  fun createPrisonerConvictionStatusChangedAdditionalInformationJson(prisonerId: String): String {
    val jsonValues = HashMap<String, String>()

    jsonValues["nomsNumber"] = prisonerId

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

  private fun getJsonString(entry: Map.Entry<String, Any>): String {
    return when (entry.value) {
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
}
