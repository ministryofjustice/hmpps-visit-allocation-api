package uk.gov.justice.digital.hmpps.visitallocationapi.integration.domainevents

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener.Companion.PRISON_VISITS_ALLOCATION_ALERTS_QUEUE_CONFIG_KEY
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.DomainEvent
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
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

  @BeforeEach
  fun cleanQueue() {
    purgeQueue(sqsClient, queueUrl)
    purgeQueue(sqsDlqClient!!, dlqUrl!!)
  }

  fun purgeQueue(client: SqsAsyncClient, url: String) {
    client.purgeQueue(PurgeQueueRequest.builder().queueUrl(url).build()).get()
  }

  fun createDomainEvent(eventType: String, additionalInformation: String = "test"): DomainEvent {
    return DomainEvent(eventType = eventType, additionalInformation)
  }

  fun createDomainEventPublishRequest(eventType: String): PublishRequest? {
    return PublishRequest.builder()
      .topicArn(topicArn)
      .message(objectMapper.writeValueAsString(createDomainEvent(eventType, ""))).build()
  }

  fun createDomainEventJson(eventType: String, additionalInformation: String): String {
    return "{\"eventType\":\"$eventType\",\"additionalInformation\":$additionalInformation}"
  }
}
