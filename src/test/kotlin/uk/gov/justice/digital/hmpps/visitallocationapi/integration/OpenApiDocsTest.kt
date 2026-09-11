package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import io.swagger.v3.oas.models.PathItem.HttpMethod.GET
import io.swagger.v3.oas.models.PathItem.HttpMethod.PUT
import io.swagger.v3.parser.OpenAPIV3Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.GET_VISIT_ORDER_HISTORY
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_BALANCE
import uk.gov.justice.digital.hmpps.visitallocationapi.controller.VO_BALANCE_DETAILED
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class OpenApiDocsTest : IntegrationTestBase() {
  @LocalServerPort
  private val port: Int = 0

  @Test
  fun `open api docs are available`() {
    webTestClient.get()
      .uri("/swagger-ui/index.html?configUrl=/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `open api docs redirect to correct page`() {
    webTestClient.get()
      .uri("/swagger-ui.html")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().is3xxRedirection
      .expectHeader().value("Location") { it.contains("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config") }
  }

  @Test
  fun `the open api json contains documentation`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("paths").isNotEmpty
  }

  @Test
  fun `the open api json contains the version number`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("info.version").isEqualTo(DateTimeFormatter.ISO_DATE.format(LocalDate.now()))
  }

  @Test
  fun `the open api json is valid and contains documentation`() {
    val result = OpenAPIV3Parser().readLocation("http://localhost:$port/v3/api-docs", null, null)
    assertThat(result.messages).isEmpty()
    assertThat(result.openAPI.paths).isNotEmpty
  }

  @Test
  fun `the client open api contains only the supported contract`() {
    val result = OpenAPIV3Parser().readLocation("http://localhost:$port/v3/api-docs/client", null, null)

    assertThat(result.messages).isEmpty()
    assertThat(result.openAPI.paths.keys).containsExactlyInAnyOrder(
      VO_BALANCE,
      VO_BALANCE_DETAILED,
      GET_VISIT_ORDER_HISTORY,
    )
    assertThat(result.openAPI.paths.getValue(VO_BALANCE).readOperationsMap().keys)
      .containsExactlyInAnyOrder(GET, PUT)
    assertThat(result.openAPI.paths.getValue(VO_BALANCE_DETAILED).readOperationsMap().keys)
      .containsExactly(GET)
    assertThat(result.openAPI.paths.getValue(GET_VISIT_ORDER_HISTORY).readOperationsMap().keys)
      .containsExactly(GET)
    assertThat(result.openAPI.components.schemas.keys).containsExactlyInAnyOrder(
      "ErrorResponse",
      "ManualBalanceAdjustmentValidationErrorResponse",
      "PrisonerBalanceAdjustmentDto",
      "PrisonerBalanceDto",
      "PrisonerDetailedBalanceDto",
      "VisitOrderHistoryAttributesDto",
      "VisitOrderHistoryDto",
    )
  }

  @Test
  fun `the open api json path security requirements are valid`() {
    val result = OpenAPIV3Parser().readLocation("http://localhost:$port/v3/api-docs", null, null)

    val securityRequirements = result.openAPI.security.flatMap { it.keys }
    result.openAPI.paths.forEach { pathItem ->
      listOfNotNull(
        pathItem.value.get,
        pathItem.value.post,
        pathItem.value.put,
        pathItem.value.delete,
      ).forEach { operation ->
        val operationSecurity = operation.security?.flatMap { it.keys } ?: emptyList()
        assertThat(operationSecurity).isSubsetOf(securityRequirements)
      }
    }
  }

  @Test
  fun `bearer scheme is configured correctly`() {
    val res = webTestClient.get().uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()

    res
      .jsonPath("$.components.securitySchemes['bearer-jwt'].type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes['bearer-jwt'].bearerFormat").isEqualTo("JWT")
      .jsonPath("$.security[0]['bearer-jwt']").value<List<Any>> { assertThat(it).isEmpty() }
  }
}
