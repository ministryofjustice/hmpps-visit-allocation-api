package uk.gov.justice.digital.hmpps.visitallocationapi.config

import io.netty.resolver.DefaultAddressResolverGroup
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration
import java.time.Duration.ofSeconds

@Configuration
class WebClientConfiguration(
  @Value("\${hmpps-auth.url}") val hmppsAuthBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${prisoner.search.url}") private val prisonSearchBaseUrl: String,
) {
  private enum class HmppsAuthClientRegistrationId(val clientRegistrationId: String) {
    PRISONER_SEARCH("other-hmpps-apis"),
  }

  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient {
    return builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)
  }

  @Bean
  fun prisonerSearchWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = getOauth2Client(authorizedClientManager, HmppsAuthClientRegistrationId.PRISONER_SEARCH.clientRegistrationId)
    return getWebClient(prisonSearchBaseUrl, oauth2Client)
  }

  private fun getOauth2Client(authorizedClientManager: OAuth2AuthorizedClientManager, clientRegistrationId: String): ServletOAuth2AuthorizedClientExchangeFilterFunction {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId(clientRegistrationId)
    return oauth2Client
  }

  private fun getExchangeStrategies(): ExchangeStrategies {
    return ExchangeStrategies.builder()
      .codecs { configurer: ClientCodecConfigurer -> configurer.defaultCodecs().maxInMemorySize(-1) }
      .build()
  }

  private fun getWebClient(baseUrl: String, oauth2Client: ServletOAuth2AuthorizedClientExchangeFilterFunction): WebClient {
    val provider = ConnectionProvider.builder("custom")
      .maxConnections(500)
      .maxIdleTime(ofSeconds(20))
      .maxLifeTime(ofSeconds(60))
      .pendingAcquireTimeout(ofSeconds(60))
      .evictInBackground(ofSeconds(120))
      .build()

    return WebClient.builder()
      .baseUrl(baseUrl)
      .clientConnector(
        ReactorClientHttpConnector(
          HttpClient.create(provider)
            .resolver(DefaultAddressResolverGroup.INSTANCE)
            .compress(true)
            .responseTimeout(ofSeconds(30)),
        ),
      )
      .apply(oauth2Client.oauth2Configuration())
      .exchangeStrategies(getExchangeStrategies())
      .build()
  }
}
