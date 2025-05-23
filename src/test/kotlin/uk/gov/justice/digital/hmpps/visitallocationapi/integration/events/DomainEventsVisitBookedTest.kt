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
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension.Companion.prisonApiMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.VisitSchedulerMockExtension.Companion.visitSchedulerMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("Test for Domain Event Visit Booked")
class DomainEventsVisitBookedTest : EventsIntegrationTestBase() {

  @Test
  fun `when domain event visit booked is found, then PVO is consumed`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, dpsPrisoner))
    dpsPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, dpsPrisoner))
    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        prisonerId = dpsPrisoner.prisonerId,
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_BOOKED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_BOOKED_EVENT_TYPE.value, domainEvent)

    // And
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then (first to spy verify calls twice, because at the end of the processing, we raise an event on the same queue which is read but ignored).
    await untilAsserted { verify(domainEventListenerSpy, times(2)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(2)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerVisitOrderUsage(any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogAllocationUsedByVisit(any(), any()) }
    await untilAsserted { verify(snsService, times(1)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(2)
    assertThat(visitOrders.filter { it.visitReference == visitReference }.size).isEqualTo(1)

    val changLog = changeLogRepository.findFirstByPrisonerIdAndChangeTypeOrderByChangeTimestampDesc(prisonerId, ChangeLogType.ALLOCATION_USED_BY_VISIT)!!
    assertThat(changLog.comment).isEqualTo("allocated to $visitReference")
  }

  @Test
  fun `when domain event visit booked is found, and no PVO is available, then VO is consumed`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, dpsPrisoner))
    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        prisonerId = dpsPrisoner.prisonerId,
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_BOOKED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_BOOKED_EVENT_TYPE.value, domainEvent)

    // And
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then (first to spy verify calls twice, because at the end of the processing, we raise an event on the same queue which is read but ignored).
    await untilAsserted { verify(domainEventListenerSpy, times(2)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(2)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerVisitOrderUsage(any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogAllocationUsedByVisit(any(), any()) }
    await untilAsserted { verify(snsService, times(1)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(1)
    assertThat(visitOrders.filter { it.visitReference == visitReference }.size).isEqualTo(1)

    val changLog = changeLogRepository.findFirstByPrisonerIdAndChangeTypeOrderByChangeTimestampDesc(prisonerId, ChangeLogType.ALLOCATION_USED_BY_VISIT)!!
    assertThat(changLog.comment).isEqualTo("allocated to $visitReference")
  }

  @Test
  fun `when domain event visit booked is found, and PVO or VO is available, then a negative VO is created`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        prisonerId = dpsPrisoner.prisonerId,
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_BOOKED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_BOOKED_EVENT_TYPE.value, domainEvent)

    // And
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then (first to spy verify calls twice, because at the end of the processing, we raise an event on the same queue which is read but ignored).
    await untilAsserted { verify(domainEventListenerSpy, times(2)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(2)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerVisitOrderUsage(any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogAllocationUsedByVisit(any(), any()) }
    await untilAsserted { verify(snsService, times(1)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.size).isEqualTo(0)

    val negativeVisitOrders = negativeVisitOrderRepository.findAll()
    assertThat(negativeVisitOrders.size).isEqualTo(1)
    assertThat(negativeVisitOrders.filter { it.visitReference == visitReference }.size).isEqualTo(1)

    val changLog = changeLogRepository.findFirstByPrisonerIdAndChangeTypeOrderByChangeTimestampDesc(prisonerId, ChangeLogType.ALLOCATION_USED_BY_VISIT)!!
    assertThat(changLog.comment).isEqualTo("allocated to $visitReference")
  }

  @Test
  fun `when domain event visit booked is found, but prisoner is on remand, no extra processing is done`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        prisonerId = dpsPrisoner.prisonerId,
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_BOOKED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_BOOKED_EVENT_TYPE.value, domainEvent)

    // And
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Remand"))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(0)).processPrisonerVisitOrderUsage(any()) }
    await untilAsserted { verify(changeLogService, times(0)).createLogAllocationUsedByVisit(any(), any()) }
    await untilAsserted { verify(snsService, times(0)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
  }

  @Test
  fun `when domain event visit booked is found, but prison is owned by NOMIS, no extra processing is done`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        prisonerId = dpsPrisoner.prisonerId,
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_BOOKED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_BOOKED_EVENT_TYPE.value, domainEvent)

    // And
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(0)).processPrisonerVisitOrderUsage(any()) }
    await untilAsserted { verify(changeLogService, times(0)).createLogAllocationUsedByVisit(any(), any()) }
    await untilAsserted { verify(snsService, times(0)).sendPrisonAllocationAdjustmentCreatedEvent(any()) }

    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
  }

  @Test
  fun `when domain event visit booked is found, but visit-scheduler call fails, then message is sent to DLQ`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, dpsPrisoner))
    dpsPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, dpsPrisoner))
    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        prisonerId = dpsPrisoner.prisonerId,
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_BOOKED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_BOOKED_EVENT_TYPE.value, domainEvent)

    // And
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, null, HttpStatus.NOT_FOUND)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN", convictedStatus = "Convicted"))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }

  @Test
  fun `when domain event visit booked is found, but prisoner-search-client call fails, then message is sent to DLQ`() {
    // Given
    val visitReference = "ab-cd-ef-gh"
    val prisonId = "HEI"
    val prisonerId = "AA123456"

    val visit = createVisitDto(visitReference, prisonerId, prisonId)

    val dpsPrisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    dpsPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, dpsPrisoner))
    dpsPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, dpsPrisoner))
    dpsPrisoner.changeLogs.add(
      ChangeLog(
        changeTimestamp = LocalDateTime.now().minusSeconds(1),
        changeType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        prisonerId = dpsPrisoner.prisonerId,
        comment = "Random existing changeLog",
        prisoner = dpsPrisoner,
        visitOrderBalance = 2,
        privilegedVisitOrderBalance = 1,
      ),
    )
    prisonerDetailsRepository.saveAndFlush(dpsPrisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.VISIT_BOOKED_EVENT_TYPE.value,
      createVisitBookedAdditionalInformationJson(visitReference),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.VISIT_BOOKED_EVENT_TYPE.value, domainEvent)

    // And
    visitSchedulerMockServer.stubGetVisitByReference(visitReference, visit)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, null, HttpStatus.INTERNAL_SERVER_ERROR)
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }
}
