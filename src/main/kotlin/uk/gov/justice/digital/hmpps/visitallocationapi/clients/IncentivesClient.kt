package uk.gov.justice.digital.hmpps.visitallocationapi.clients

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonerIncentivesDto
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.NotFoundException
import java.util.concurrent.TimeoutException

@Component
class IncentivesClient(
  @Qualifier("incentivesWebClient") private val webClient: WebClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getPrisonerIncentiveReviewHistory(prisonerId: String): PrisonerIncentivesDto {
    LOG.info("Calling incentives-api to get prisoner incentive code for prisoner ID $prisonerId")

    return webClient.get()
      .uri("/incentive-reviews/prisoner/$prisonerId")
      .retrieve()
      .bodyToMono(PrisonerIncentivesDto::class.java)
      .block() ?: throw TimeoutException("Request timed out while fetching prisoner incentive level for prisoner ID $prisonerId")
  }

  fun getPrisonIncentiveLevels(prisonId: String): List<PrisonIncentiveAmountsDto> {
    LOG.info("Calling incentives-api to get prison all incentive levels for prison $prisonId")

    val uri = "/incentive/prison-levels/$prisonId"
    return webClient.get()
      .uri(uri)
      .retrieve()
      .bodyToMono<List<PrisonIncentiveAmountsDto>>()
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getPrisonIncentiveLevels Failed for get request $uri")
          Mono.error(e)
        } else {
          LOG.error("getPrisonIncentiveLevels NOT_FOUND for get request $uri")
          Mono.error { NotFoundException("Incentive levels not found for prison $prisonId, $e") }
        }
      }
      .block() ?: throw TimeoutException("Request timed out while fetching incentive levels for $prisonId")
  }

  fun getPrisonIncentiveLevelByLevelCode(prisonId: String, levelCode: String): PrisonIncentiveAmountsDto {
    LOG.info("Calling incentives-api to get incentive levels for prison - $prisonId")

    val uri = "/incentive/prison-levels/$prisonId/level/$levelCode"
    return webClient.get()
      .uri(uri)
      .retrieve()
      .bodyToMono(PrisonIncentiveAmountsDto::class.java)
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getPrisonIncentiveLevelByLevelCode Failed for get request $uri")
          Mono.error(e)
        } else {
          LOG.error("getPrisonIncentiveLevelByLevelCode NOT_FOUND for get request $uri")
          Mono.error { NotFoundException("Incentive levels not found for prison $prisonId, level $levelCode, $e") }
        }
      }
      .block() ?: throw TimeoutException("Request timed out while fetching incentive level for $prisonId, level $levelCode")
  }

  private fun isNotFoundError(e: Throwable?) = e is WebClientResponseException && e.statusCode == NOT_FOUND
}
