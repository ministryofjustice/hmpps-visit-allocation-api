package uk.gov.justice.digital.hmpps.visitallocationapi.clients

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Application insights now controlled by the spring-boot-starter dependency.  However when the key is not specified
 * we don't get a telemetry bean and application won't start.  Therefore need this backup configuration.
 */
@Configuration
class ApplicationInsightsConfiguration {

  @Bean
  fun telemetryClient(): TelemetryClient = TelemetryClient()
}
