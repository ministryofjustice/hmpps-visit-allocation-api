package uk.gov.justice.digital.hmpps.visitallocationapi.clients

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prison.api.VisitBalancesDto

@Component
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getBookingVisitBalances(prisonerId: String): VisitBalancesDto {
    LOG.debug("Entered prison-api client - getVisitBalances for prisoner $prisonerId")
    val uri = "/api/bookings/offenderNo/$prisonerId/visit/balances"
    return webClient.get()
      .uri(uri)
      .retrieve()
      .bodyToMono<VisitBalancesDto>()
      .onErrorResume { e ->
        LOG.error("getVisitBalances Failed get request $uri, exception $e")
        Mono.error(e)
      }
      .block() ?: throw IllegalStateException("Received null response from $uri, cannot get balance in nomis for prisoner $prisonerId")
  }
}
