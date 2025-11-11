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
  @param:Value("\${hmpps-auth.url}") val hmppsAuthBaseUri: String,
  @param:Value("\${prisoner.search.url}") private val prisonSearchBaseUrl: String,
  @param:Value("\${incentives.api.url}") private val incentivesBaseUrl: String,
  @param:Value("\${prison.api.url}") private val prisonApiBaseUrl: String,
  @param:Value("\${visit-scheduler.api.url}") private val visitSchedulerBaseUrl: String,

  @param:Value("\${api.timeout:10s}") private val apiTimeout: Duration,
  @param:Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
) {
  private enum class HmppsAuthClientRegistrationId(val clientRegistrationId: String) {
    VISIT_SCHEDULER("other-hmpps-apis"),
    PRISONER_SEARCH("other-hmpps-apis"),
    PRISON_API("other-hmpps-apis"),
    INCENTIVES("other-hmpps-apis"),
  }

  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)

  @Bean
  fun visitSchedulerHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(visitSchedulerBaseUrl, healthTimeout)

  @Bean
  fun incentivesWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = HmppsAuthClientRegistrationId.INCENTIVES.clientRegistrationId, url = incentivesBaseUrl, apiTimeout)

  @Bean
  fun prisonerSearchWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = HmppsAuthClientRegistrationId.PRISONER_SEARCH.clientRegistrationId, url = prisonSearchBaseUrl, apiTimeout)

  @Bean
  fun prisonApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = HmppsAuthClientRegistrationId.PRISON_API.clientRegistrationId, url = prisonApiBaseUrl, apiTimeout)

  @Bean
  fun visitSchedulerWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = HmppsAuthClientRegistrationId.VISIT_SCHEDULER.clientRegistrationId, url = visitSchedulerBaseUrl, apiTimeout)
}
