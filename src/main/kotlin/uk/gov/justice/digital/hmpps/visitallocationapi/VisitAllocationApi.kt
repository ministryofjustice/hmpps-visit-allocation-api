package uk.gov.justice.digital.hmpps.visitallocationapi

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.visitallocationapi.batch.BatchManager

@SpringBootApplication
class VisitAllocationApi

@Configuration
@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
@EnableAutoConfiguration(
  exclude = [
    DataSourceAutoConfiguration::class,
    DataSourceTransactionManagerAutoConfiguration::class,
    FlywayAutoConfiguration::class,
    HibernateJpaAutoConfiguration::class,
    DataJpaRepositoriesAutoConfiguration::class,
  ],
)
@ComponentScan(basePackageClasses = [BatchManager::class])
class VisitAllocationBatchApi

fun main(args: Array<String>) {
  if (System.getenv("BATCH_ENABLED").equals("true", ignoreCase = true)) {
    SpringApplication.run(VisitAllocationBatchApi::class.java, *args)
  } else {
    runApplication<VisitAllocationApi>(*args)
  }
}
