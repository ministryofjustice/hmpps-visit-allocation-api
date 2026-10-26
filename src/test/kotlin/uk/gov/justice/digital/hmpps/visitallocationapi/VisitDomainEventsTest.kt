package uk.gov.justice.digital.hmpps.visitallocationapi

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.VisitSchedulerClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.VisitDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType.VISIT_BOOKED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.TestObjectMapper
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.DomainEventListenerService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerVisitOrderRefundService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerVisitOrderUsageService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.SnsService
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.SQSMessage
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers.DomainEventHandlerRegistry
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers.VisitBookedEventHandler
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.events.handlers.VisitCancelledEventHandler
import java.util.UUID
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
class VisitDomainEventsTest {
  @Mock
  private lateinit var prisonService: PrisonService

  @Mock
  private lateinit var visitSchedulerClient: VisitSchedulerClient

  @Mock
  private lateinit var prisonerSearchClient: PrisonerSearchClient

  @Mock
  private lateinit var usageService: PrisonerVisitOrderUsageService

  @Mock
  private lateinit var refundService: PrisonerVisitOrderRefundService

  @Mock
  private lateinit var snsService: SnsService

  @Mock
  private lateinit var changeLogService: ChangeLogService

  private lateinit var listener: DomainEventListener
  private val visit = VisitDto("ab-cd-ef-gh", "A1234AA", "HEI")

  @BeforeEach
  fun setUp() {
    val mapper = TestObjectMapper.mapper
    val registry = DomainEventHandlerRegistry(
      mock(),
      mock(),
      VisitBookedEventHandler(mapper, prisonService, visitSchedulerClient, prisonerSearchClient, usageService, snsService, changeLogService),
      VisitCancelledEventHandler(mapper, prisonService, visitSchedulerClient, prisonerSearchClient, refundService, snsService, changeLogService),
    )
    listener = DomainEventListener(DomainEventListenerService(registry), mapper, true)
  }

  @ParameterizedTest
  @EnumSource(DomainEventType::class, names = ["VISIT_BOOKED_EVENT_TYPE", "VISIT_CANCELLED_EVENT_TYPE"])
  fun `video events are acknowledged without downstream calls or allocation side effects`(eventType: DomainEventType) {
    consume(eventType, "SOCIAL_VIDEO")

    verifyNoInteractions(visitSchedulerClient, prisonerSearchClient, prisonService, usageService, refundService, changeLogService, snsService)
  }

  @ParameterizedTest
  @EnumSource(DomainEventType::class, names = ["VISIT_BOOKED_EVENT_TYPE", "VISIT_CANCELLED_EVENT_TYPE"])
  fun `explicit social events retain allocation processing`(eventType: DomainEventType) {
    assertAllocationProcessing(eventType, "SOCIAL")
  }

  @ParameterizedTest
  @EnumSource(DomainEventType::class, names = ["VISIT_BOOKED_EVENT_TYPE", "VISIT_CANCELLED_EVENT_TYPE"])
  fun `legacy events without visit type retain allocation processing`(eventType: DomainEventType) {
    assertAllocationProcessing(eventType, null)
  }

  private fun assertAllocationProcessing(eventType: DomainEventType, visitType: String?) {
    val changeLogReference = UUID.randomUUID()
    val changeLog = mock<ChangeLog>()
    whenever(visitSchedulerClient.getVisitByReference(visit.reference)).thenReturn(visit)
    whenever(prisonerSearchClient.getPrisonerById(visit.prisonerId)).thenReturn(
      PrisonerDto(visit.prisonerId, visit.prisonCode, "IN", visit.prisonCode, "Convicted"),
    )
    whenever(prisonService.getPrisonEnabledForDpsByCode(visit.prisonCode)).thenReturn(true)
    whenever(changeLogService.findChangeLogForPrisonerByReference(visit.prisonerId, changeLogReference)).thenReturn(changeLog)
    if (eventType == VISIT_BOOKED_EVENT_TYPE) {
      whenever(usageService.processPrisonerVisitOrderUsage(visit, null)).thenReturn(changeLogReference)
    } else {
      whenever(refundService.processPrisonerVisitOrderRefund(visit, null)).thenReturn(changeLogReference)
    }

    consume(eventType, visitType)

    if (eventType == VISIT_BOOKED_EVENT_TYPE) {
      verify(usageService).processPrisonerVisitOrderUsage(visit, null)
      verifyNoInteractions(refundService)
    } else {
      verify(refundService).processPrisonerVisitOrderRefund(visit, null)
      verifyNoInteractions(usageService)
    }
    verify(changeLogService).findChangeLogForPrisonerByReference(visit.prisonerId, changeLogReference)
    verify(snsService).sendPrisonAllocationAdjustmentCreatedEvent(changeLog)
  }

  private fun consume(eventType: DomainEventType, visitType: String?) {
    val additionalInformation = mutableMapOf<String, Any>("reference" to visit.reference, "eventAuditId" to 123)
    if (visitType != null) {
      additionalInformation["visitType"] = visitType
    }
    val message = TestObjectMapper.mapper.writeValueAsString(
      mapOf("eventType" to eventType.value, "additionalInformation" to additionalInformation),
    )

    listener.processMessage(SQSMessage("Notification", message)).get(10, TimeUnit.SECONDS)
  }
}
