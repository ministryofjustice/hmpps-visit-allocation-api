package uk.gov.justice.digital.hmpps.visitallocationapi.integration.events

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension.Companion.prisonApiMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.VisitSchedulerMockExtension.Companion.visitSchedulerMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@DisplayName("Test for Domain Event Visit Cancelled")
class DomainEventsVisitCancelledTest : EventsIntegrationTestBase() {

  @Test
  fun `when domain event visit cancelled is found, if VO used then VO is refunded`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.visitOrders.add(
      VisitOrder(
        type = VisitOrderType.VO,
        status = VisitOrderStatus.USED,
        createdTimestamp = LocalDateTime.now().minusDays(1),
        visitReference = visitReference,
        prisoner = dpsPrisoner,
      ),
    )

    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.BATCH_PROCESS,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
        reference = UUID.randomUUID(),
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then (first to spy verify calls twice, because at the end of the processing, we raise an event on the same queue which is read but ignored).
    await untilAsserted { verify(domainEventListenerSpy, times(2)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(2)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerVisitOrderRefund(any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogAllocationRefundedByVisitCancelled(any(), any()) }
    await untilAsserted { verify(snsService, times(1)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(1)
    assertThat(visitOrders.first { it.type == VisitOrderType.VO }.createdTimestamp.toLocalDate()).isEqualTo(LocalDate.now().minusDays(1))
    assertThat(visitOrders.filter { it.visitReference == visitReference }.size).isEqualTo(0)

    val changLog = changeLogRepository.findAll().first { it.changeType == ChangeLogType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED }
    assertThat(changLog.comment).isEqualTo("allocated refunded as $visitReference cancelled")
  }

  @Test
  fun `when domain event visit cancelled is found, if negative VO used, then negative VO is removed`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.negativeVisitOrders.add(
      NegativeVisitOrder(
        type = VisitOrderType.VO,
        status = NegativeVisitOrderStatus.USED,
        createdTimestamp = LocalDateTime.now().minusDays(1),
        visitReference = visitReference,
        prisoner = dpsPrisoner,
      ),
    )

    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.BATCH_PROCESS,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
        reference = UUID.randomUUID(),
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then (first to spy verify calls twice, because at the end of the processing, we raise an event on the same queue which is read but ignored).
    await untilAsserted { verify(domainEventListenerSpy, times(2)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(2)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerVisitOrderRefund(any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogAllocationRefundedByVisitCancelled(any(), any()) }
    await untilAsserted { verify(snsService, times(1)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(0)

    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.size).isEqualTo(0)

    val changLog = changeLogRepository.findAll().first { it.changeType == ChangeLogType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED }
    assertThat(changLog.comment).isEqualTo("allocated refunded as $visitReference cancelled")
  }

  @Test
  fun `when domain event visit cancelled is found, no vo associated with visit can be found, then new vo is created for prisoner`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then (first to spy verify calls twice, because at the end of the processing, we raise an event on the same queue which is read but ignored).
    await untilAsserted { verify(domainEventListenerSpy, times(2)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(2)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerVisitOrderRefund(any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogAllocationRefundedByVisitCancelled(any(), any()) }
    await untilAsserted { verify(snsService, times(1)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(1)

    val changLog = changeLogRepository.findAll().first { it.changeType == ChangeLogType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED }
    assertThat(changLog.comment).isEqualTo("allocated refunded as $visitReference cancelled")
  }

  @Test
  fun `when domain event visit cancelled is found, but visit-scheduler call fails, then message is sent to DLQ`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.negativeVisitOrders.add(
      NegativeVisitOrder(
        type = VisitOrderType.VO,
        status = NegativeVisitOrderStatus.USED,
        createdTimestamp = LocalDateTime.now().minusDays(1),
        visitReference = visitReference,
        prisoner = dpsPrisoner,
      ),
    )

    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.BATCH_PROCESS,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
        reference = UUID.randomUUID(),
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, null, HttpStatus.INTERNAL_SERVER_ERROR)
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }

  @Test
  fun `when domain event visit cancelled is found, but prison API returns an error, then message is sent to DLQ`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.negativeVisitOrders.add(
      NegativeVisitOrder(
        type = VisitOrderType.VO,
        status = NegativeVisitOrderStatus.USED,
        createdTimestamp = LocalDateTime.now().minusDays(1),
        visitReference = visitReference,
        prisoner = dpsPrisoner,
      ),
    )

    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
        reference = UUID.randomUUID(),
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false, HttpStatus.INTERNAL_SERVER_ERROR)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }

  @Test
  fun `when domain event visit cancelled is found, but prison is owned by NOMIS, then no further processing happens`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.negativeVisitOrders.add(
      NegativeVisitOrder(
        type = VisitOrderType.VO,
        status = NegativeVisitOrderStatus.USED,
        createdTimestamp = LocalDateTime.now().minusDays(1),
        visitReference = visitReference,
        prisoner = dpsPrisoner,
      ),
    )

    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
        reference = UUID.randomUUID(),
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then (first to spy verify calls twice, because at the end of the processing, we raise an event on the same queue which is read but ignored).
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(0)).processPrisonerVisitOrderRefund(any()) }
    await untilAsserted { verify(changeLogService, times(0)).createLogAllocationRefundedByVisitCancelled(any(), any()) }
    await untilAsserted { verify(snsService, times(0)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
  }

  @Test
  fun `when domain event visit cancelled is found, and prisoner is at maximum VO balance, then no VO is refunded`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 26, dpsPrisoner))

    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.BATCH_PROCESS,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
        reference = UUID.randomUUID(),
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then (first to spy verify calls twice, because at the end of the processing, we raise an event on the same queue which is read but ignored).
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerVisitOrderRefund(any()) }
    await untilAsserted { verify(changeLogService, times(0)).createLogAllocationRefundedByVisitCancelled(any(), any()) }
    await untilAsserted { verify(snsService, times(0)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(26)

    val changLogCount = changeLogRepository.findAll().count { it.changeType == ChangeLogType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED }
    assertThat(changLogCount).isEqualTo(0)
  }

  @Test
  fun `when domain event visit cancelled is found, but prisoner is on remand, no processing occurs`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_CANCELLED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then (first to spy verify calls twice, because at the end of the processing, we raise an event on the same queue which is read but ignored).
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(0)).processPrisonerVisitOrderRefund(any()) }
    await untilAsserted { verify(changeLogService, times(0)).createLogAllocationRefundedByVisitCancelled(any(), any()) }
    await untilAsserted { verify(snsService, times(0)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(0)
  }
}
