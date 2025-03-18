package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper.EntityHelper
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.ChangeLogRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.TelemetryClientService
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@ExtendWith(HmppsAuthApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {
  @Autowired
  protected lateinit var objectMapper: ObjectMapper

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

  @AfterEach
  fun deleteAll() {
    entityHelper.deleteAll()
  }

  protected fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
  }
}
