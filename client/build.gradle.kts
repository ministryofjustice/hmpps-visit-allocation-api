plugins {
  kotlin("jvm")
  `java-library`
  `maven-publish`
  id("org.openapi.generator")
}

group = "uk.gov.justice.service.hmpps"
version = "0.1.0-SNAPSHOT"
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
  api(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
  api("org.springframework:spring-webflux")
  // The pinned WebClient generator uses Jackson 2 in its optional base-URL constructor.
  api("com.fasterxml.jackson.core:jackson-databind")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  runtimeOnly("io.projectreactor.netty:reactor-netty-http")
  runtimeOnly("org.springframework:spring-context")
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
