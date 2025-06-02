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
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension.Companion.prisonApiMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension.Companion.prisonerSearchMockServer
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate

@DisplayName("Test for Domain Event Prisoner Merged")
class DomainEventsPrisonerMergedTest : EventsIntegrationTestBase() {
  @Test
  fun `when domain event prisoner merged is found, then prisoner balance is synced`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "HEI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerBalanceFromEventChange(any(), any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerRemoved(any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogSyncEventChange(any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(5)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails[0].prisonerId).isEqualTo(prisonerId)
  }

  @Test
  fun `when domain event prisoner merged is found, but an error is returned from prisoner search the message is sent to DLQ`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "MDI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, null, HttpStatus.NOT_FOUND)
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(0, 0))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }

  @Test
  fun `when domain event prisoner merged is found, but a 404 not found is returned from prison API then message is skipped`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "MDI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, null, HttpStatus.NOT_FOUND)
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 0 }
  }

  @Test
  fun `when domain event prisoner merged is found, then new prisoner balance is synced`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "HEI"

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerBalanceFromEventChange(any(), any()) }
    await untilAsserted { verify(nomisSyncService, times(1)).syncPrisonerRemoved(any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogSyncEventChange(any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val visitOrders = visitOrderRepository.findAll()
    assertThat(visitOrders.filter { it.status == VisitOrderStatus.AVAILABLE }.size).isEqualTo(5)

    val prisonerDetails = prisonerDetailsRepository.findAll()
    assertThat(prisonerDetails.size).isEqualTo(1)
    assertThat(prisonerDetails[0].prisonerId).isEqualTo(prisonerId)
    assertThat(prisonerDetails[0].lastVoAllocatedDate).isEqualTo(LocalDate.now())
    assertThat(prisonerDetails[0].lastPvoAllocatedDate).isNull()
  }

  @Test
  fun `when prisoner is in DPS prison post merge if removed prisoner has more VOs the difference is added to new prisoner`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "HEI"

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // prisoner is in a DPS enabled prison
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))

    // new prisoner has 2 VOs and 3 PVOs, removed prisoner has 5 VOs and 8 PVOs
    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null)
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 3, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val removedPrisoner = PrisonerDetails(prisonerId = removedPrisonerId, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null)
    removedPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 5, removedPrisoner))
    removedPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 8, removedPrisoner))
    prisonerDetailsRepository.save(removedPrisoner)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerMerge(any(), any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogAllocationForPrisonerMerge(any(), any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val availableVisitOrdersForPrisoner = visitOrderRepository.findAll().filter { it.status == VisitOrderStatus.AVAILABLE && it.prisonerId == prisonerId }
    // prisoner should end up wth 5 VOs and 8 PVOs
    assertThat(availableVisitOrdersForPrisoner.filter { it.type == VisitOrderType.VO }.size).isEqualTo(5)
    assertThat(availableVisitOrdersForPrisoner.filter { it.type == VisitOrderType.PVO }.size).isEqualTo(8)
  }

  @Test
  fun `when prisoner is in DPS prison post merge if removed prisoner has same or less VOs no VOs are added to new prisoner`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "HEI"

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // prisoner is in a DPS enabled prison
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))

    // new prisoner has 2 VOs and 3 PVOs, removed prisoner has 1 VOs and 3 PVOs
    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null)
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 3, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val removedPrisoner = PrisonerDetails(prisonerId = removedPrisonerId, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null)
    removedPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 1, removedPrisoner))
    removedPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 3, removedPrisoner))
    prisonerDetailsRepository.save(removedPrisoner)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerMerge(any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val availableVisitOrdersForPrisoner = visitOrderRepository.findAll().filter { it.status == VisitOrderStatus.AVAILABLE && it.prisonerId == prisonerId }
    // prisoner should end up wth same VOs and PVOs as earlier
    assertThat(availableVisitOrdersForPrisoner.filter { it.type == VisitOrderType.VO }.size).isEqualTo(2)
    assertThat(availableVisitOrdersForPrisoner.filter { it.type == VisitOrderType.PVO }.size).isEqualTo(3)
    verify(changeLogService, times(0)).createLogAllocationForPrisonerMerge(any(), any(), any())
  }

  @Test
  fun `when prisoner is in DPS prison post merge but does not exist on allocation DB a new prisoner is created and VOs allocated same as removed prisoner`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "HEI"

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // prisoner is in a DPS enabled prison
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))

    // new prisoner does not exist on the DB

    // removed prisoner exists on the DB with 3 VOs and 5 PVOs
    val removedPrisoner = PrisonerDetails(prisonerId = removedPrisonerId, lastVoAllocatedDate = LocalDate.now(), lastPvoAllocatedDate = null)
    removedPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 3, removedPrisoner))
    removedPrisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 5, removedPrisoner))
    prisonerDetailsRepository.save(removedPrisoner)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerMerge(any(), any()) }
    await untilAsserted { verify(changeLogService, times(1)).createLogAllocationForPrisonerMerge(any(), any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val availableVisitOrdersForPrisoner = visitOrderRepository.findAll().filter { it.status == VisitOrderStatus.AVAILABLE && it.prisonerId == prisonerId }
    // prisoner should end up wth 3 VOs and 5 PVOs
    assertThat(availableVisitOrdersForPrisoner.filter { it.type == VisitOrderType.VO }.size).isEqualTo(3)
    assertThat(availableVisitOrdersForPrisoner.filter { it.type == VisitOrderType.PVO }.size).isEqualTo(5)
  }

  @Test
  fun `when prisoner is in DPS prison post merge but both prisoners do not exist on allocation DB a new prisoner is created but no VOs are allocated`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "HEI"

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // prisoner is in a DPS enabled prison
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, true)
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))

    // new prisoner and removed prisoner both do not exist on the DB

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilAsserted { verify(domainEventListenerSpy, times(1)).processMessage(any()) }
    await untilAsserted { verify(domainEventListenerServiceSpy, times(1)).handleMessage(any()) }
    await untilAsserted { verify(processPrisonerService, times(1)).processPrisonerMerge(any(), any()) }
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }

    val availableVisitOrdersForPrisoner = visitOrderRepository.findAll().filter { it.status == VisitOrderStatus.AVAILABLE && it.prisonerId == prisonerId }
    // prisoner created but no VOs allocated
    assertThat(availableVisitOrdersForPrisoner.filter { it.type == VisitOrderType.VO }.size).isEqualTo(0)
    assertThat(availableVisitOrdersForPrisoner.filter { it.type == VisitOrderType.PVO }.size).isEqualTo(0)
    verify(changeLogService, times(0)).createLogAllocationForPrisonerMerge(any(), any(), any())
  }

  @Test
  fun `when domain event prisoner merged is found but 500 response from prison-api get service prison, then message fails`() {
    // Given
    val prisonerId = "AA123456"
    val removedPrisonerId = "BB123456"
    val prisonId = "HEI"

    val prisoner = PrisonerDetails(prisonerId = prisonerId, lastVoAllocatedDate = LocalDate.now(), LocalDate.now())
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.VO, 2, prisoner))
    prisoner.visitOrders.addAll(createVisitOrders(VisitOrderType.PVO, 1, prisoner))
    prisonerDetailsRepository.save(prisoner)

    val domainEvent = createDomainEventJson(
      DomainEventType.PRISONER_MERGED_EVENT_TYPE.value,
      createPrisonerMergedAdditionalInformationJson(prisonerId = prisonerId, removedPrisonerId = removedPrisonerId),
    )
    val publishRequest = createDomainEventPublishRequest(DomainEventType.PRISONER_MERGED_EVENT_TYPE.value, domainEvent)

    // And
    prisonerSearchMockServer.stubGetPrisonerById(prisonerId = prisonerId, createPrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = "IN"))
    prisonApiMockServer.stubGetVisitBalances(prisonerId = prisonerId, createVisitBalancesDto(3, 2))
    prisonApiMockServer.stubGetPrisonEnabledForDps(prisonId, false, HttpStatus.INTERNAL_SERVER_ERROR)

    // When
    awsSnsClient.publish(publishRequest).get()

    // Then
    await untilCallTo { domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get() } matches { it == 0 }
    await untilCallTo { domainEventsSqsDlqClient!!.countMessagesOnQueue(domainEventsDlqUrl!!).get() } matches { it == 1 }
  }
}
