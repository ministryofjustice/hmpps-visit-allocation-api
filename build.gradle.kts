plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "9.3.0"
  kotlin("plugin.spring") version "2.3.0"
  kotlin("plugin.jpa") version "2.3.0"
  kotlin("plugin.allopen") version "2.3.0"
  id("org.owasp.dependencycheck") version "12.1.9"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.8.2")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.15")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.6.3")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")

  runtimeOnly("org.postgresql:postgresql:42.7.8")
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.8.2")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.37") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("org.testcontainers:localstack:1.21.4")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
}

kotlin {
  jvmToolchain(25)
}

java {
  sourceCompatibility = JavaVersion.VERSION_24
  targetCompatibility = JavaVersion.VERSION_24
}

allOpen {
  annotation("jakarta.persistence.Entity")
  annotation("jakarta.persistence.Embeddable")
  annotation("jakarta.persistence.MappedSuperclass")
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24
  }
}

dependencyCheck {
  nvd.datafeedUrl = "file:///opt/vulnz/cache"
}
