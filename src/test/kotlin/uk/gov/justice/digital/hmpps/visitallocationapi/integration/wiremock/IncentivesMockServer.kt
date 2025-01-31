package uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonerIncentivesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.MockUtils.Companion.createJsonResponseBuilder
import uk.gov.justice.digital.hmpps.visitallocationapi.integration.wiremock.MockUtils.Companion.getJsonString

class IncentivesMockServer : WireMockServer(8095) {
  fun stubGetPrisonerIncentiveReviewHistory(prisonerId: String, prisonerIncentivesDto: PrisonerIncentivesDto?, httpStatus: HttpStatus = HttpStatus.NOT_FOUND) {
    val responseBuilder = createJsonResponseBuilder()
    stubFor(
      get("/incentive-reviews/prisoner/$prisonerId")
        .willReturn(
          if (prisonerIncentivesDto == null) {
            responseBuilder
              .withStatus(httpStatus.value())
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(prisonerIncentivesDto))
          },
        ),
    )
  }

  fun stubGetAllPrisonIncentiveLevels(prisonId: String, prisonIncentiveAmountsDtoList: List<PrisonIncentiveAmountsDto>?, httpStatus: HttpStatus = HttpStatus.NOT_FOUND) {
    val responseBuilder = createJsonResponseBuilder()
    stubFor(
      get("/incentive/prison-levels/$prisonId")
        .willReturn(
          if (prisonIncentiveAmountsDtoList == null) {
            responseBuilder
              .withStatus(httpStatus.value())
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(prisonIncentiveAmountsDtoList))
          },
        ),
    )
  }

  fun stubGetPrisonIncentiveLevels(prisonId: String, levelCode: String, prisonIncentiveAmountsDto: PrisonIncentiveAmountsDto?, httpStatus: HttpStatus = HttpStatus.NOT_FOUND) {
    val responseBuilder = createJsonResponseBuilder()
    stubFor(
      get("/incentive/prison-levels/$prisonId/level/$levelCode")
        .willReturn(
          if (prisonIncentiveAmountsDto == null) {
            responseBuilder
              .withStatus(httpStatus.value())
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(prisonIncentiveAmountsDto))
          },
        ),
    )
  }
}

class IncentivesMockExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val incentivesMockServer = IncentivesMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    incentivesMockServer.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    incentivesMockServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    incentivesMockServer.stop()
  }
}
