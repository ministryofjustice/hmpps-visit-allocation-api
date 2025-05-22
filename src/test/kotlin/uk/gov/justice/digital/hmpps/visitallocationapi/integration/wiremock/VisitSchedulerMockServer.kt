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
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.VisitDto
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.MockUtils.Companion.getJsonString

class VisitSchedulerMockServer : WireMockServer(8097) {
  fun stubGetVisitByReference(visitReference: String, visit: VisitDto?, httpStatus: HttpStatus? = null) {
    val responseBuilder = aResponse()
      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

    stubFor(
      get("/visits/$visitReference")
        .willReturn(
          if (visit == null) {
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
              .withBody(getJsonString(visit))
          },
        ),
    )
  }
}

class VisitSchedulerMockExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val visitSchedulerMockServer = VisitSchedulerMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    visitSchedulerMockServer.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    visitSchedulerMockServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    visitSchedulerMockServer.stop()
  }
}
