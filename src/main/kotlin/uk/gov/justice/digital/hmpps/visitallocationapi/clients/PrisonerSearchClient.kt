package uk.gov.justice.digital.hmpps.visitallocationapi.clients

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import java.util.concurrent.TimeoutException

@Component
class PrisonerSearchClient(
  @Qualifier("prisonerSearchWebClient") private val webClient: WebClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getPrisonerById(prisonerId: String): PrisonerDto {
    LOG.info("Calling prisoner-search to get prisoner by ID $prisonerId")

    return webClient.get()
      .uri("/prisoner/$prisonerId")
      .retrieve()
      .bodyToMono(PrisonerDto::class.java)
      .block() ?: throw TimeoutException("Request timed out while fetching prisoner with ID $prisonerId")
  }
}
