package uk.gov.justice.digital.hmpps.visitallocationapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.PublishEventException
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class SnsService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
  @Value("\${feature.events.sns.enabled:true}")
  private val snsEventsEnabled: Boolean,
) {

  companion object {
    const val TOPIC_ID = "domainevents"
    const val EVENT_ZONE_ID = "Europe/London"
    const val EVENT_PRISON_VISIT_VERSION = 1
    const val EVENT_PRISON_ALLOCATION_ADJUSTMENT_CREATED = "prison-visit-allocation.adjustment.created"
    const val EVENT_PRISON_ALLOCATION_ADJUSTMENT_CREATED_DESC = "Prisoner balance adjusted via nightly allocation-api batch job"
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val domainEventsTopic by lazy { hmppsQueueService.findByTopicId(TOPIC_ID) ?: throw RuntimeException("Topic with name $TOPIC_ID doesn't exist") }
  private val domainEventsTopicClient by lazy { domainEventsTopic.snsClient }

  fun LocalDateTime.toOffsetDateFormat(): String = atZone(ZoneId.of(EVENT_ZONE_ID)).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  fun sendPrisonAllocationAdjustmentCreatedEvent(changeLog: ChangeLog) {
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType = EVENT_PRISON_ALLOCATION_ADJUSTMENT_CREATED,
        version = EVENT_PRISON_VISIT_VERSION,
        description = EVENT_PRISON_ALLOCATION_ADJUSTMENT_CREATED_DESC,
        occurredAt = changeLog.changeTimestamp.toOffsetDateFormat(),
        prisonerId = changeLog.prisonerId,
        additionalInformation = AdditionalInformation(
          prisonerId = changeLog.prisonerId,
          adjustmentId = changeLog.id,
        ),
      ),
    )
  }

  private fun publishToDomainEventsTopic(payloadEvent: HMPPSDomainEvent) {
    if (!snsEventsEnabled) {
      LOG.warn("Publish to domain events topic Disabled")
      return
    }
    LOG.debug("Entered : publishToDomainEventsTopic {}", payloadEvent)

    try {
      val messageAttributes = mutableMapOf(
        "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(payloadEvent.eventType).build(),
      )

      val publishRequest = PublishRequest.builder().topicArn(domainEventsTopic.arn)
        .message(objectMapper.writeValueAsString(payloadEvent))
        .messageAttributes(messageAttributes)
        .build()

      val result = domainEventsTopicClient.publish(publishRequest).join()

      telemetryClient.trackEvent(
        "${payloadEvent.eventType}-domain-event",
        mapOf("messageId" to result.messageId(), "adjustmentId" to payloadEvent.additionalInformation.adjustmentId.toString()),
        null,
      )
    } catch (e: Throwable) {
      val message = "Failed (publishToDomainEventsTopic) to publish Event $payloadEvent.eventType to $TOPIC_ID"
      LOG.error(message, e)
      throw PublishEventException(message, e)
    }
  }
}

internal data class AdditionalInformation(
  val prisonerId: String,
  val adjustmentId: Long,
)

internal data class HMPPSDomainEvent(
  val eventType: String,
  val version: Int,
  val description: String,
  val occurredAt: String,
  val prisonerId: String,
  val additionalInformation: AdditionalInformation,
)
