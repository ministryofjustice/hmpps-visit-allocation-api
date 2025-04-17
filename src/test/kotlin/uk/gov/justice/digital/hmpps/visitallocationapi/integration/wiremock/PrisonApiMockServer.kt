package uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.MockUtils.Companion.getJsonString

class PrisonApiMockServer : WireMockServer(8096) {
  fun stubGetVisitBalances(prisonerId: String, visitBalances: VisitBalancesDto?) {
    val responseBuilder = aResponse()
      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

    stubFor(
      get("/api/bookings/offenderNo/$prisonerId/visit/balances")
        .willReturn(
          if (visitBalances == null) {
            responseBuilder
              .withStatus(HttpStatus.NOT_FOUND.value())
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(visitBalances))
          },
        ),
    )
  }
}

class PrisonApiMockExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val prisonApiMockServer = PrisonApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    prisonApiMockServer.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    prisonApiMockServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    prisonApiMockServer.stop()
  }
}
