package uk.gov.justice.digital.hmpps.visitallocationapi.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.PublishEventException
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.publish
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class SnsService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  @param:Qualifier("objectMapper")
  private val objectMapper: ObjectMapper,
  @param:Value("\${feature.events.sns.enabled:true}")
  private val snsEventsEnabled: Boolean,
  private val changeLogService: ChangeLogService,
) {

  companion object {
    const val TOPIC_ID = "domainevents"
    const val EVENT_ZONE_ID = "Europe/London"
    const val EVENT_PRISON_VISIT_VERSION = 1

    const val EVENT_PRISON_ALLOCATION_ADJUSTMENT_CREATED = "prison-visit-allocation.adjustment.created"
    const val EVENT_PRISON_ALLOCATION_ADJUSTMENT_CREATED_DESC = "Prisoner balance adjusted by hmpps-visit-allocation-api"

    const val EVENT_PRISON_PRISONER_BALANCE_RESET = "prison-visit-allocation.balance.reset"
    const val EVENT_PRISON_PRISONER_BALANCE_RESET_DESC = "Prisoner balance reset by hmpps-visit-allocation-api"

    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val domainEventsTopic by lazy { hmppsQueueService.findByTopicId(TOPIC_ID) ?: throw RuntimeException("Topic with name $TOPIC_ID doesn't exist") }

  fun LocalDateTime.toOffsetDateFormat(): String = atZone(ZoneId.of(EVENT_ZONE_ID)).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  fun sendPrisonAllocationAdjustmentCreatedEvent(changeLog: ChangeLog) {
    val event = HMPPSAdjustmentCreatedDomainEvent(
      eventType = EVENT_PRISON_ALLOCATION_ADJUSTMENT_CREATED,
      version = EVENT_PRISON_VISIT_VERSION,
      description = EVENT_PRISON_ALLOCATION_ADJUSTMENT_CREATED_DESC,
      occurredAt = changeLog.changeTimestamp.toOffsetDateFormat(),
      personReference = PersonReference(identifiers = listOf(PersonIdentifier("NOMIS", changeLog.prisonerId))),
      additionalInformation = AdditionalInformation(
        prisonerId = changeLog.prisonerId,
        adjustmentId = changeLog.id.toString(),
        hasBalanceChanged = hasBalanceChanged(changeLog),
      ),
    )

    publishAdjustmentCreatedToDomainEventsTopic(event)
  }

  fun sendPrisonAllocationPrisonerBalanceResetEvent(prisonerId: String) {
    val event = HMPPSBalanceResetDomainEvent(
      eventType = EVENT_PRISON_PRISONER_BALANCE_RESET,
      version = EVENT_PRISON_VISIT_VERSION,
      description = EVENT_PRISON_PRISONER_BALANCE_RESET_DESC,
      occurredAt = LocalDateTime.now().toOffsetDateFormat(),
      personReference = PersonReference(identifiers = listOf(PersonIdentifier("NOMIS", prisonerId))),
      additionalInformation = BalanceResetAdditionalInformation(
        prisonerId = prisonerId,
      ),
    )

    publishBalanceResetEventToDomainEventsTopic(event)
  }

  private fun publishAdjustmentCreatedToDomainEventsTopic(payloadEvent: HMPPSAdjustmentCreatedDomainEvent) {
    if (!snsEventsEnabled) {
      LOG.warn("Publish to domain events topic Disabled")
      return
    }
    LOG.debug("Entered : publishAdjustmentCreatedToDomainEventsTopic {}", payloadEvent)

    try {
      val result = domainEventsTopic.publish(
        payloadEvent.eventType,
        objectMapper.writeValueAsString(payloadEvent),
      )

      telemetryClient.trackEvent(
        "${payloadEvent.eventType}-domain-event",
        mapOf("messageId" to result.messageId(), "adjustmentId" to payloadEvent.additionalInformation.adjustmentId),
        null,
      )
    } catch (e: Throwable) {
      val message = "Failed (publishAdjustmentCreatedToDomainEventsTopic) to publish Event $payloadEvent.eventType to $TOPIC_ID"
      LOG.error(message, e)
      throw PublishEventException(message, e)
    }
  }

  private fun hasBalanceChanged(changeLog: ChangeLog): Boolean {
    val prisonerChangeLogs = changeLogService.findAllChangeLogsForPrisoner(changeLog.prisonerId).sortedBy { it.id }

    val currentIndex = prisonerChangeLogs.indexOfFirst { it.id == changeLog.id }
    if (currentIndex == -1) {
      return true // Default to true, to avoid assuming balance hasn't changed.
    }

    val currentEntry = prisonerChangeLogs[currentIndex]
    val previousEntry = if (currentIndex > 0) prisonerChangeLogs[currentIndex - 1] else null

    if (previousEntry == null) {
      return true
    }

    val previousVoBalance = previousEntry.visitOrderBalance
    val previousPvoBalance = previousEntry.privilegedVisitOrderBalance

    return currentEntry.visitOrderBalance != previousVoBalance || currentEntry.privilegedVisitOrderBalance != previousPvoBalance
  }

  private fun publishBalanceResetEventToDomainEventsTopic(payloadEvent: HMPPSBalanceResetDomainEvent) {
    if (!snsEventsEnabled) {
      LOG.warn("Publish to domain events topic Disabled")
      return
    }
    LOG.debug("Entered : publishBalanceResetEventToDomainEventsTopic {}", payloadEvent)

    try {
      val result = domainEventsTopic.publish(
        payloadEvent.eventType,
        objectMapper.writeValueAsString(payloadEvent),
      )

      telemetryClient.trackEvent(
        "${payloadEvent.eventType}-domain-event",
        mapOf("messageId" to result.messageId(), "prisonerId" to payloadEvent.additionalInformation.prisonerId),
        null,
      )
    } catch (e: Throwable) {
      val message = "Failed (publishBalanceResetEventToDomainEventsTopic) to publish Event $payloadEvent.eventType to $TOPIC_ID"
      LOG.error(message, e)
      throw PublishEventException(message, e)
    }
  }
}

internal data class HMPPSAdjustmentCreatedDomainEvent(
  val eventType: String,
  val version: Int,
  val detailUrl: String? = null,
  val description: String,
  val occurredAt: String,
  val personReference: PersonReference,
  val additionalInformation: AdditionalInformation,
)

internal data class AdditionalInformation(
  val prisonerId: String,
  val adjustmentId: String,
  val hasBalanceChanged: Boolean,
)

internal data class HMPPSBalanceResetDomainEvent(
  val eventType: String,
  val version: Int,
  val detailUrl: String? = null,
  val description: String,
  val occurredAt: String,
  val personReference: PersonReference,
  val additionalInformation: BalanceResetAdditionalInformation,
)

internal data class BalanceResetAdditionalInformation(
  val prisonerId: String,
)

internal data class PersonReference(
  val identifiers: List<PersonIdentifier>,
)

internal data class PersonIdentifier(val type: String, val value: String)
