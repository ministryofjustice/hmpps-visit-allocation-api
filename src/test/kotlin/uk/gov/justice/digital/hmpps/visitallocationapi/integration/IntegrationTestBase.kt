package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.EntityHelper
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonApiMockExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.PrisonerSearchMockExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.ChangeLogRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderHistoryRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(
  PrisonApiMockExtension::class,
  PrisonerSearchMockExtension::class,
  HmppsAuthApiExtension::class,
)
@AutoConfigureWebTestClient
abstract class IntegrationTestBase {

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  @Autowired
  lateinit var entityHelper: EntityHelper

  @MockitoSpyBean
  lateinit var visitOrderRepository: VisitOrderRepository

  @MockitoSpyBean
  lateinit var negativeVisitOrderRepository: NegativeVisitOrderRepository

  @MockitoSpyBean
  lateinit var prisonerDetailsRepository: PrisonerDetailsRepository

  @MockitoSpyBean
  lateinit var changeLogRepository: ChangeLogRepository

  @MockitoSpyBean
  lateinit var visitOrderHistoryRepository: VisitOrderHistoryRepository

  @MockitoSpyBean
  lateinit var telemetryClientService: TelemetryClientService

  protected lateinit var startVisitAllocationJobRoleHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUpRoles() {
    startVisitAllocationJobRoleHttpHeaders =
      setAuthorisation(roles = listOf("ROLE_START_VISIT_ALLOCATION"))
  }

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  @BeforeEach
  @AfterEach
  fun deleteAll() {
    entityHelper.deleteAll()
  }

  protected fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
  }

  protected fun createVisitOrders(visitOrderType: VisitOrderType, amountToCreate: Int, prisoner: PrisonerDetails, visitOrderStatus: VisitOrderStatus = VisitOrderStatus.AVAILABLE): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(amountToCreate) {
      visitOrders.add(
        VisitOrder(
          type = visitOrderType,
          status = visitOrderStatus,
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

  protected fun createPrisonerDto(prisonerId: String, prisonId: String = "HEI", inOutStatus: String = "IN", lastPrisonId: String = "BRI", convictedStatus: String? = "Convicted"): PrisonerDto = PrisonerDto(prisonerId = prisonerId, prisonId = prisonId, inOutStatus = inOutStatus, lastPrisonId = lastPrisonId, convictedStatus = convictedStatus)
}
