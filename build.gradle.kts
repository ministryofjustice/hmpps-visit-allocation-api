plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "10.3.1"
  kotlin("plugin.spring") version "2.3.21"
  kotlin("plugin.jpa") version "2.3.21"
  kotlin("plugin.allopen") version "2.3.21"
  id("org.owasp.dependencycheck") version "12.2.2"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.4.0")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.3.2")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")

  runtimeOnly("org.postgresql:postgresql:42.7.11")
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.4.0")
  testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.42") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("org.testcontainers:testcontainers-localstack:2.0.5")
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

tasks.withType<Test>().configureEach {
  jvmArgs("-Dspring.test.context.cache.pause=never")
}
