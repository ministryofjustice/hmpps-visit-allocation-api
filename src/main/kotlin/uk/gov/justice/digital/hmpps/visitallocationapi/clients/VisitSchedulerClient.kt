package uk.gov.justice.digital.hmpps.visitallocationapi.clients

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.VisitDto
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.NotFoundException

@Component
class VisitSchedulerClient(
  @Qualifier("visitSchedulerWebClient") private val webClient: WebClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getVisitByReference(visitReference: String): VisitDto {
    val uri = "/visits/$visitReference"
    return webClient.get()
      .uri(uri)
      .accept(MediaType.APPLICATION_JSON)
      .retrieve()
      .bodyToMono<VisitDto>()
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getVisitByReference Failed for get request $uri")
          Mono.error(e)
        } else {
          LOG.error("getVisitByReference NOT_FOUND for get request $uri")
          Mono.error { NotFoundException("Visit not found for reference $visitReference, $e") }
        }
      }
      .block() ?: throw IllegalStateException("timeout response from visit-scheduler for getVisitByReference with reference $visitReference")
  }

  private fun isNotFoundError(e: Throwable?) = e is WebClientResponseException && e.statusCode == NOT_FOUND
}
