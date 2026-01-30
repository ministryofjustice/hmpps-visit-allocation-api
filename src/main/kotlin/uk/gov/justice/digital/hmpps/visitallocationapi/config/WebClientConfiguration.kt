package uk.gov.justice.digital.hmpps.visitallocationapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import uk.gov.justice.hmpps.kotlin.auth.service.GlobalPrincipalOAuth2AuthorizedClientService
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
  private val clientRegistrationId = "hmpps-api"

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ClientRegistrationRepository,
  ): OAuth2AuthorizedClientManager {
    val authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    val authorizedClientManager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
      clientRegistrationRepository,
      GlobalPrincipalOAuth2AuthorizedClientService(clientRegistrationRepository),
    )
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider)
    return authorizedClientManager
  }

  private fun getWebClient(
    url: String,
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    url = url,
    registrationId = clientRegistrationId,
    timeout = apiTimeout,
  )

  @Bean
  fun incentivesWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = getWebClient(incentivesBaseUrl, authorizedClientManager, builder)

  @Bean
  fun prisonerSearchWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = getWebClient(prisonSearchBaseUrl, authorizedClientManager, builder)

  @Bean
  fun prisonApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = getWebClient(prisonApiBaseUrl, authorizedClientManager, builder)

  @Bean
  fun visitSchedulerWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = getWebClient(visitSchedulerBaseUrl, authorizedClientManager, builder)

  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)

  @Bean
  fun visitSchedulerHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(visitSchedulerBaseUrl, healthTimeout)

  @Bean
  fun prisonApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonApiBaseUrl, healthTimeout)

  @Bean
  fun prisonerSearchHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonSearchBaseUrl, healthTimeout)

  @Bean
  fun incentivesHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(incentivesBaseUrl, healthTimeout)
}
