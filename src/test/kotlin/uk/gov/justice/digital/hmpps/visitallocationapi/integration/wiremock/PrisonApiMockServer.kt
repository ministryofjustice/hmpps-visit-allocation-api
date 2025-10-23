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
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.ServicePrisonDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.MockUtils.Companion.getJsonString

class PrisonApiMockServer : WireMockServer(8096) {
  fun stubGetVisitBalances(prisonerId: String, visitBalances: VisitBalancesDto?, httpStatus: HttpStatus? = null) {
    val responseBuilder = aResponse()
      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

    stubFor(
      get("/api/bookings/offenderNo/$prisonerId/visit/balances")
        .willReturn(
          if (visitBalances == null) {
            if (httpStatus != null) {
              responseBuilder
                .withStatus(httpStatus.value())
            } else {
              responseBuilder
                .withStatus(HttpStatus.NOT_FOUND.value())
            }
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(visitBalances))
          },
        ),
    )
  }

  fun stubGetPrisonEnabledForDps(prisonId: String, enabled: Boolean, httpStatus: HttpStatus? = null) {
    val responseBuilder = aResponse()
      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

    stubFor(
      get("/api/agency-switches/VISIT_ALLOCATION/agency/$prisonId")
        .willReturn(
          if (enabled) {
            responseBuilder.withStatus(HttpStatus.NO_CONTENT.value())
          } else {
            if (httpStatus != null) {
              responseBuilder.withStatus(httpStatus.value())
            } else {
              responseBuilder.withStatus(HttpStatus.NOT_FOUND.value())
            }
          },
        ),
    )
  }

  fun stubGetAllServicePrisonsEnabledForDps(prisons: List<ServicePrisonDto>?, httpStatus: HttpStatus? = null) {
    val responseBuilder = aResponse()
      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

    stubFor(
      get("/api/agency-switches/VISIT_ALLOCATION")
        .willReturn(
          if (prisons == null) {
            if (httpStatus != null) {
              responseBuilder
                .withStatus(httpStatus.value())
            } else {
              responseBuilder
                .withStatus(HttpStatus.NOT_FOUND.value())
            }
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(prisons))
          },
        ),
    )
  }

  fun stubGetAllActivePrisons(prisons: List<ServicePrisonDto>?, httpStatus: HttpStatus? = null) {
    val responseBuilder = aResponse()
      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

    stubFor(
      get("/api/agencies/prisons")
        .willReturn(
          if (prisons == null) {
            if (httpStatus != null) {
              responseBuilder
                .withStatus(httpStatus.value())
            } else {
              responseBuilder
                .withStatus(HttpStatus.NOT_FOUND.value())
            }
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(prisons))
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
