import org.springframework.boot.gradle.plugin.SpringBootPlugin
import java.util.jar.JarFile

plugins {
  kotlin("jvm")
  `java-library`
  `maven-publish`
  id("org.openapi.generator")
}

group = "uk.gov.justice.service.hmpps"
version = "1.0.0-SNAPSHOT"
base.archivesName.set("hmpps-visit-allocation-client")

repositories {
  mavenCentral()
}

kotlin {
  jvmToolchain(25)
  compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
}
java {
  sourceCompatibility = JavaVersion.VERSION_24
  targetCompatibility = JavaVersion.VERSION_24
  withSourcesJar()
}

dependencies {
  api(platform(SpringBootPlugin.BOM_COORDINATES))
  api("org.springframework:spring-webflux")
  api("com.fasterxml.jackson.core:jackson-databind")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}

val specFile = rootProject.layout.buildDirectory.file("openapi/client-api.json")
val generatedDirectory = layout.buildDirectory.dir("generated/openapi")
val requireOpenApiSpec = tasks.register("requireOpenApiSpec") {
  mustRunAfter(rootProject.tasks.named("exportOpenApi"))
  doLast {
    check(specFile.get().asFile.isFile) {
      "OpenAPI specification is missing. Start the local API and run ./gradlew exportOpenApi first."
    }
  }
}

openApiValidate {
  inputSpec.set(specFile)
}
tasks.named("openApiValidate") {
  dependsOn(requireOpenApiSpec)
  mustRunAfter(rootProject.tasks.named("exportOpenApi"))
}
openApiGenerate {
  generatorName.set("kotlin")
  library.set("jvm-spring-webclient")
  inputSpec.set(specFile)
  outputDir.set(generatedDirectory)
  packageName.set("uk.gov.justice.digital.hmpps.visitallocationclient")
  apiPackage.set("uk.gov.justice.digital.hmpps.visitallocationclient.api")
  modelPackage.set("uk.gov.justice.digital.hmpps.visitallocationclient.model")
  // History DTOs serialize LocalDateTime without an offset.
  typeMappings.set(mapOf("DateTime" to "java.time.LocalDateTime"))
  configOptions.set(
    mapOf(
      "serializationLibrary" to "jackson",
      "dateLibrary" to "java8",
      "requestDateConverter" to "toString",
      "useSpringBoot3" to "true",
      "omitGradleWrapper" to "true",
    ),
  )
  globalProperties.set(mapOf("apiTests" to "false", "modelTests" to "false"))
  cleanupOutput.set(true)
}
tasks.named("openApiGenerate") {
  dependsOn("openApiValidate")
}
kotlin.sourceSets.named("main") {
  kotlin.srcDir(generatedDirectory.map { it.dir("src/main/kotlin") })
}
tasks.named("compileKotlin") { dependsOn("openApiGenerate") }
tasks.named("sourcesJar") { dependsOn("openApiGenerate") }
publishing {
  publications {
    create<MavenPublication>("client") {
      artifactId = "hmpps-visit-allocation-client"
      from(components["java"])
      pom {
        name.set("HMPPS Visit Allocation Client")
        description.set("Generated JVM client for the HMPPS Visit Allocation API")
      }
    }
  }
}

val verifyClientArtifact = tasks.register("verifyClientArtifact") {
  group = "verification"
  description = "Verify the client JAR surface and published dependency metadata."
  dependsOn("jar", "generatePomFileForClientPublication")

  val clientJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
  inputs.file(clientJar)
  inputs.file(layout.buildDirectory.file("publications/client/pom-default.xml"))

  doLast {
    val jarFile = clientJar.get().asFile
    val entries = JarFile(jarFile).use { jar ->
      jar.entries().asSequence().map { it.name }.toSet()
    }

    val apiClasses = entries
      .filter { it.startsWith("uk/gov/justice/digital/hmpps/visitallocationclient/api/") && it.endsWith(".class") && '$' !in it }
      .map { it.substringAfterLast('/').removeSuffix(".class") }
      .toSet()
    check(apiClasses == setOf("BalanceControllerApi", "VisitOrderHistoryControllerApi")) {
      "Unexpected generated API classes in ${jarFile.name}: $apiClasses"
    }

    val modelClasses = entries
      .filter { it.startsWith("uk/gov/justice/digital/hmpps/visitallocationclient/model/") && it.endsWith(".class") && '$' !in it }
      .map { it.substringAfterLast('/').removeSuffix(".class") }
      .toSet()
    val expectedModels = setOf(
      "ErrorResponse",
      "ManualBalanceAdjustmentValidationErrorResponse",
      "PrisonerBalanceAdjustmentDto",
      "PrisonerBalanceDto",
      "PrisonerDetailedBalanceDto",
      "VisitOrderHistoryAttributesDto",
      "VisitOrderHistoryDto",
    )
    check(modelClasses == expectedModels) {
      "Unexpected generated model classes in ${jarFile.name}: $modelClasses"
    }
    check(entries.none { it.startsWith("META-INF/openapi/") }) {
      "The OpenAPI contract must not be packaged in ${jarFile.name}"
    }

    val pom = layout.buildDirectory.file("publications/client/pom-default.xml").get().asFile.readText()
    val forbiddenDependencyMarkers = listOf("spring-security", "oauth")
    check(forbiddenDependencyMarkers.none { it in pom.lowercase() }) {
      "The client publication must not include Spring Security or OAuth dependencies"
    }

    val forbiddenRuntimeDependencies = configurations.getByName("runtimeClasspath")
      .resolvedConfiguration
      .resolvedArtifacts
      .map { "${it.moduleVersion.id.group}:${it.name}" }
      .filter { dependency -> forbiddenDependencyMarkers.any { it in dependency.lowercase() } }
    check(forbiddenRuntimeDependencies.isEmpty()) {
      "The client runtime must not include Spring Security or OAuth dependencies: $forbiddenRuntimeDependencies"
    }
  }
}

tasks.named("check") { dependsOn(verifyClientArtifact) }
tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenLocal>().configureEach {
  dependsOn("check")
}
