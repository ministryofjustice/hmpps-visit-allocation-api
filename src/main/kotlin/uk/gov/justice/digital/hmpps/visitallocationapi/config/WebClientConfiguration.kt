package uk.gov.justice.digital.hmpps.visitallocationapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${hmpps-auth.url}") val hmppsAuthBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${prisoner.search.url}") private val prisonSearchBaseUrl: String,
  @Value("\${incentives.api.url}") private val incentivesBaseUrl: String,

  @Value("\${api.timeout:10s}") private val apiTimeout: Duration,
) {
  private enum class HmppsAuthClientRegistrationId(val clientRegistrationId: String) {
    PRISONER_SEARCH("other-hmpps-apis"),
    INCENTIVES("other-hmpps-apis"),
  }

  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient {
    return builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)
  }

  @Bean
  fun incentivesWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = HmppsAuthClientRegistrationId.INCENTIVES.clientRegistrationId, url = incentivesBaseUrl, apiTimeout)

  @Bean
  fun prisonerSearchWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = HmppsAuthClientRegistrationId.PRISONER_SEARCH.clientRegistrationId, url = prisonSearchBaseUrl, apiTimeout)
}
