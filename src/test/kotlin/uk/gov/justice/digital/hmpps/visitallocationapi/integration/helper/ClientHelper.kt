package uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper

import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.web.reactive.function.BodyInserters

fun callPost(
  bodyValue: Any? = null,
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec = if (bodyValue == null) {
  webTestClient.post().uri(url)
    .headers(authHttpHeaders)
    .exchange()
} else {
  webTestClient.post().uri(url)
    .headers(authHttpHeaders)
    .body(BodyInserters.fromValue(bodyValue))
    .exchange()
}

fun callGet(
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
) = webTestClient.get().uri(url)
  .headers(authHttpHeaders)
  .exchange()
