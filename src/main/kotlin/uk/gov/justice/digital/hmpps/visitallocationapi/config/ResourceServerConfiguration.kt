package uk.gov.justice.digital.hmpps.visitallocationapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.hmpps.kotlin.auth.dsl.ResourceServerConfigurationCustomizer

@Configuration
class ResourceServerConfiguration {
  @Bean
  fun resourceServerCustomizer() = ResourceServerConfigurationCustomizer {
    unauthorizedRequestPaths {
      addPaths = setOf(
        // Protected by the ingress - see Kube config in helm_deploy
        "/visits/allocation/job/start",
        "/queue-admin/retry-all-dlqs",
      )
    }
  }
}
