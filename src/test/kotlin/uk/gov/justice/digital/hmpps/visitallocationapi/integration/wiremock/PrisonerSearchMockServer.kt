package uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.MockUtils.Companion.createJsonResponseBuilder
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.MockUtils.Companion.getJsonString

class PrisonerSearchMockServer : WireMockServer(8094) {
  fun stubGetPrisonerById(prisonerId: String, prisoner: PrisonerDto?, httpStatus: HttpStatus = HttpStatus.NOT_FOUND) {
    val responseBuilder = createJsonResponseBuilder()
    stubFor(
      get("/prisoner/$prisonerId")
        .willReturn(
          if (prisoner == null) {
            responseBuilder
              .withStatus(httpStatus.value())
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(prisoner))
          },
        ),
    )
  }

  fun stubGetConvictedPrisoners(prisonId: String, convictedPrisoners: List<PrisonerDto>?, httpStatus: HttpStatus = HttpStatus.NOT_FOUND) {
    val responseBuilder = createJsonResponseBuilder()
    stubFor(
      post("/attribute-search?size=$10000")
        .willReturn(
          if (convictedPrisoners == null) {
            responseBuilder
              .withStatus(httpStatus.value())
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(convictedPrisoners))
          },
        ),
    )
  }
}

class PrisonerSearchMockExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val prisonerSearchMockServer = PrisonerSearchMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    prisonerSearchMockServer.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    prisonerSearchMockServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    prisonerSearchMockServer.stop()
  }
}
