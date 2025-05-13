package uk.gov.justice.digital.hmpps.visitallocationapi.clients

import io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.ServicePrisonDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.VisitBalancesDto

@Component
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val SERVICE_NAME = "VISIT_ALLOCATION"
  }

  fun getBookingVisitBalances(prisonerId: String): VisitBalancesDto? {
    LOG.debug("Entered prison-api client - getVisitBalances for prisoner $prisonerId")
    val uri = "/api/bookings/offenderNo/$prisonerId/visit/balances"
    return webClient.get()
      .uri(uri)
      .retrieve()
      .bodyToMono<VisitBalancesDto>()
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getVisitBalances Failed get request $uri, exception $e")
          Mono.error(e)
        } else {
          LOG.warn("getVisitBalances Failed get request $uri")
          Mono.empty()
        }
      }
      .block()
  }

  fun getPrisonEnabledForDps(prisonId: String): Boolean {
    LOG.debug("Entered prison-api client - getPrisonEnabledForDps for prison $prisonId")

    return webClient.get()
      .uri("/api/service-prisons/$SERVICE_NAME/prison/$prisonId")
      .exchangeToMono { response ->
        when (response.statusCode()) {
          NO_CONTENT -> {
            Mono.just(true)
          }
          NOT_FOUND -> {
            Mono.just(false)
          }
          else -> {
            response.createException().flatMap { Mono.error(it) }
          }
        }
      }
      .block() ?: throw IllegalStateException("timeout response from prison-api for getPrisonEnabledForDps with $prisonId")
  }

  fun getAllServicePrisonsEnabledForDps(): List<ServicePrisonDto> {
    LOG.debug("Entered prison-api client - getAllServicePrisonsEnabledForDps")
    val uri = "/api/service-prisons/$SERVICE_NAME"

    return webClient.get()
      .uri(uri)
      .retrieve()
      .bodyToMono<List<ServicePrisonDto>>()
      .onErrorResume { e ->
        if (isNotFoundError(e)) {
          LOG.warn("getAllServicePrisonsEnabledForDps returned 404, returning empty list for $uri")
          Mono.just(emptyList())
        } else {
          LOG.error("getAllServicePrisonsEnabledForDps Failed get request $uri", e)
          Mono.error(e)
        }
      }
      .block() ?: throw IllegalStateException("timeout response from prison-api for getAllServicePrisonsEnabledForDps")
  }

  private fun isNotFoundError(e: Throwable?) = e is WebClientResponseException && e.statusCode == NOT_FOUND
}
