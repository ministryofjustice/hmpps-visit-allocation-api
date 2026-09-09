import io.swagger.v3.parser.OpenAPIV3Parser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

buildscript {
  repositories { mavenCentral() }
  dependencies { classpath("io.swagger.parser.v3:swagger-parser:2.1.48") }
}

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.6"
  kotlin("plugin.spring") version "2.4.10"
  kotlin("plugin.jpa") version "2.4.10"
  kotlin("plugin.allopen") version "2.4.10"
  id("org.owasp.dependencycheck") version "13.0.0"
  id("org.openapi.generator") version "7.24.0" apply false
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:3.0.1")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.4.1")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")

  runtimeOnly("org.postgresql:postgresql:42.7.13")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:3.0.1")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.48") {
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

tasks.withType<Test>().configureEach {
  jvmArgs("-Dspring.test.context.cache.pause=never")
}

val docsUrl = providers.gradleProperty("openApiUrl").orElse("http://localhost:8079/v3/api-docs/client")
val specFile = layout.buildDirectory.file("openapi/client-api.json")

tasks.register("exportOpenApi") {
  group = "client"
  description = "Export and validate the consumer client OpenAPI contract from a running local allocation API."
  inputs.property("openApiUrl", docsUrl)
  outputs.file(specFile)
  // An explicit export must always fetch the running application's current contract.
  outputs.upToDateWhen { false }
  doLast {
    val target = specFile.get().asFile.toPath()
    Files.createDirectories(target.parent)
    // A failed export must not leave an old contract available for a subsequent client build.
    Files.deleteIfExists(target)
    try {
      val request = HttpRequest.newBuilder(URI.create(docsUrl.get()))
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json")
        .GET()
        .build()
      val response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        .send(request, HttpResponse.BodyHandlers.ofString())
      check(response.statusCode() == 200) { "Documentation endpoint returned HTTP ${response.statusCode()}" }
      check(response.body().trimStart().startsWith("{")) { "Documentation endpoint did not return JSON" }
      val parsed = OpenAPIV3Parser().readContents(response.body(), null, null)
      check(parsed.openAPI != null && !parsed.openAPI.paths.isNullOrEmpty() && parsed.messages.isNullOrEmpty()) {
        "Invalid or empty OpenAPI contract: ${parsed.messages}"
      }
      val temporary = Files.createTempFile(target.parent, "allocation-api-", ".json")
      try {
        Files.writeString(temporary, response.body())
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
      } finally {
        Files.deleteIfExists(temporary)
      }
      logger.lifecycle("Exported ${parsed.openAPI.paths.size} paths to $target")
    } catch (exception: Exception) {
      throw GradleException("OpenAPI export failed. Start the local API with API docs enabled, then run ./gradlew exportOpenApi. ${exception.message}", exception)
    }
  }
}

tasks.register("refreshAndPublishClientToMavenLocal") {
  group = "client"
  description = "Export, validate, generate, build and publish the JVM client to Maven Local."
  dependsOn("exportOpenApi", ":client:publishToMavenLocal")
}
