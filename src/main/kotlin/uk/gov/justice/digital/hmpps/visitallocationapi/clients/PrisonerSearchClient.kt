package uk.gov.justice.digital.hmpps.visitallocationapi.clients

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.AttributeSearchPrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ConvictedStatus
import java.util.concurrent.TimeoutException

@Component
class PrisonerSearchClient(
  @param:Qualifier("prisonerSearchWebClient") private val webClient: WebClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)

    const val DEFAULT_PAGE_SIZE = 5000
    const val RESPONSE_FIELD = "prisonerNumber"
  }

  fun getPrisonerById(prisonerId: String): PrisonerDto {
    LOG.info("Calling prisoner-search to get prisoner by ID $prisonerId")

    return webClient.get()
      .uri("/prisoner/$prisonerId")
      .retrieve()
      .bodyToMono(PrisonerDto::class.java)
      .block() ?: throw TimeoutException("Request timed out while fetching prisoner with ID $prisonerId")
  }

  fun getConvictedPrisonersByPrisonId(prisonId: String): RestPage<AttributeSearchPrisonerDto> {
    LOG.info("Calling prisoner-search to get all convicted prisoners for prison $prisonId")
    val requestBody = AttributeSearch(
      queries = listOf(
        AttributeQuery(
          matchers = listOf(
            Matcher(attribute = "prisonId", condition = "IS", searchTerm = prisonId),
            Matcher(attribute = "convictedStatus", condition = "IS", searchTerm = ConvictedStatus.CONVICTED.value),
            Matcher(attribute = "status", condition = "STARTSWITH", searchTerm = "ACTIVE"),
          ),
        ),
      ),
    )

    return webClient
      .post()
      .uri("/attribute-search?size=$DEFAULT_PAGE_SIZE&responseFields=$RESPONSE_FIELD")
      .bodyValue(requestBody)
      .accept(MediaType.APPLICATION_JSON)
      .retrieve()
      .bodyToMono<RestPage<AttributeSearchPrisonerDto>>()
      .block() ?: throw TimeoutException("Request timed out while fetching all prisoners from prison $prisonId")
  }

  fun getAllPrisonersByPrisonId(prisonId: String): RestPage<AttributeSearchPrisonerDto> {
    LOG.info("Calling prisoner-search to get all prisoners for prison $prisonId")
    val requestBody = AttributeSearch(
      queries = listOf(
        AttributeQuery(
          matchers = listOf(
            Matcher(attribute = "prisonId", condition = "IS", searchTerm = prisonId),
          ),
        ),
      ),
    )

    return webClient
      .post()
      .uri("/attribute-search?size=$DEFAULT_PAGE_SIZE&responseFields=$RESPONSE_FIELD")
      .bodyValue(requestBody)
      .accept(MediaType.APPLICATION_JSON)
      .retrieve()
      .bodyToMono<RestPage<AttributeSearchPrisonerDto>>()
      .block() ?: throw TimeoutException("Request timed out while fetching all prisoners from prison $prisonId")
  }

  fun findMergedPrisonerByIdentifierTypeMerged(mergedPrisonerId: String): RestPage<AttributeSearchPrisonerDto> {
    LOG.info("Calling prisoner-search to check if prisoner with ID $mergedPrisonerId is merged")
    val requestBody = AttributeSearch(
      queries = listOf(
        AttributeQuery(
          matchers = listOf(
            Matcher(attribute = "identifiers.type", condition = "IS", searchTerm = "MERGED"),
            Matcher(attribute = "identifiers.value", condition = "IS", searchTerm = mergedPrisonerId),
          ),
        ),
      ),
    )

    return webClient
      .post()
      .uri("/attribute-search?size=$DEFAULT_PAGE_SIZE&responseFields=$RESPONSE_FIELD")
      .bodyValue(requestBody)
      .accept(MediaType.APPLICATION_JSON)
      .retrieve()
      .bodyToMono<RestPage<AttributeSearchPrisonerDto>>()
      .block() ?: throw TimeoutException("Request timed out while fetching merged prisoner with ID $mergedPrisonerId")
  }
}

data class AttributeSearch(
  val joinType: String = "AND",
  val queries: List<AttributeQuery>,
)

data class AttributeQuery(
  val joinType: String = "AND",
  val matchers: List<Matcher>,
)

data class Matcher(
  val type: String = "String",
  val attribute: String,
  val condition: String,
  val searchTerm: String,
)
