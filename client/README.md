# Visit Allocation JVM client POC

This module generates a Kotlin client for the consumer-facing balance and visit-order
history endpoints. The JAR contains the generated client methods, their required DTOs
and the shared client infrastructure. Operational admin, queue, job and NOMIS APIs are
excluded.

Publication: `uk.gov.justice.service.hmpps:hmpps-visit-allocation-client:0.1.0-SNAPSHOT`

## Requirements

- JDK 25 to run this repository's Gradle build; the client bytecode targets Java 24.
- Consumers running Java 24 or newer. The client uses Kotlin 2.4.10.
- The configured dependency baseline is Spring Boot 4.1.1 / Spring Framework 7.0.9.
- A running local allocation API with `/v3/api-docs/client` enabled for the export step.
  Follow [the API setup instructions](../README.md#running), including PostgreSQL,
  LocalStack and the `dev` profile. Start/restart the API from the source revision
  you want represented in the client.

There is no Node.js requirement and no remote publishing or release workflow in this POC.

## 1. Export the client contract

From the repository root, with the API running on its usual local port:

```bash
./gradlew exportOpenApi
```

This fetches `http://localhost:8079/v3/api-docs/client`, validates the response and writes
`build/openapi/client-api.json`. It always fetches again when explicitly invoked.
A failed export removes the previous file so an old contract cannot be mistaken for
a successful refresh. Invalid, empty and non-JSON responses fail the task.

For an API running on another local port:

```bash
./gradlew exportOpenApi -PopenApiUrl=http://localhost:8080/v3/api-docs/client
```

The task does not start the API or verify its Git revision. Restart the API and export
again after controller/DTO changes. A successful export is a snapshot: subsequent
client builds use that file and do not contact the API.

## 2. Build the client

```bash
./gradlew :client:build
```

This validates the saved spec, generates the two client API classes and their models,
compiles the library and produces:

```text
client/build/libs/hmpps-visit-allocation-client-0.1.0-SNAPSHOT.jar
client/build/libs/hmpps-visit-allocation-client-0.1.0-SNAPSHOT-sources.jar
```

The input OpenAPI document is used only at build time and is not packaged in the JAR.
Generated code is under `client/build/generated/openapi/src/main/kotlin`; do not edit
it manually.
OpenAPI Generator is pinned to 7.24.0 with the `kotlin` / `jvm-spring-webclient` generator.
Date-only fields map to `LocalDate`. Date-time fields explicitly map to `LocalDateTime`
to match the offset-free timestamps currently returned by history endpoints.
Revisit that mapping if the server introduces timestamps with offsets.
Regeneration removes its old output first, so removed endpoints do not leave stale classes.

For a clean client rebuild that retains the exported contract:

```bash
./gradlew :client:clean :client:build
```

Root `:clean` deletes the exported contract. After a root clean, export again before
building the client. Use root-qualified tasks such as `:build`, `:test`, `:check` and
`:assemble` for API-only work; unqualified Gradle task names also select matching
tasks in `client` and therefore require an exported contract.

## 3. Publish locally

After the build succeeds:

```bash
./gradlew :client:publishToMavenLocal
```

This installs the binary JAR, sources JAR, POM and Gradle module metadata into your
local Maven repository (normally `~/.m2/repository`). This snapshot version may be
overwritten during the POC.

Prefer consuming this Maven publication to adding the JAR with `files(...)`:
Gradle/Maven then resolves the HTTP, JSON and Kotlin dependencies from its metadata.
The binary is a normal library JAR, not a self-contained executable or fat JAR.
To use it on another machine, transfer/install the publication with its metadata,
or generate and publish locally on that machine; Maven Local is machine-specific.

## 4. Consume from another API

Add this to the consuming API's `build.gradle.kts` (or equivalent repository settings
if that project centralizes repositories in `settings.gradle.kts`):

```kotlin
repositories {
  mavenLocal {
    content {
      includeModule("uk.gov.justice.service.hmpps", "hmpps-visit-allocation-client")
    }
  }
  mavenCentral()
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-visit-allocation-client:0.1.0-SNAPSHOT")
}
```

For Spring Boot consumers, supply the WebClient already configured for allocation:

```kotlin
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.visitallocationclient.api.BalanceControllerApi
import java.time.Duration

// allocationWebClient includes the base URL, OAuth filter and HTTP timeout settings.
fun readBalance(allocationWebClient: WebClient, prisonerId: String) =
  BalanceControllerApi(allocationWebClient)
    .getPrisonerBalance(prisonerId)
    .block(Duration.ofSeconds(10))
```

In reactive application code, return/compose the `Mono` instead of blocking.
Create and reuse API client instances as beans. `VisitOrderHistoryControllerApi` is in
the same `.api` package; DTOs are in `.model`.

For a short local experiment with a valid token:

```kotlin
val webClient = WebClient.builder()
  .baseUrl("http://localhost:8079")
  .defaultHeader("Authorization", "Bearer $accessToken")
  .build()
val balances = BalanceControllerApi(webClient)
```

The consumer obtains/refreshes tokens and configures timeouts, tracing and any retry
policy. The client does not automatically obtain HMPPS Auth tokens or grant roles.
The publication contains no credentials, tokens or client secrets. Keep remote
publication in an organisation-controlled Maven repository.

Java can use the same JAR:

```java
import java.time.Duration;
import uk.gov.justice.digital.hmpps.visitallocationclient.api.BalanceControllerApi;
import uk.gov.justice.digital.hmpps.visitallocationclient.model.PrisonerBalanceAdjustmentDto;

var balances = new BalanceControllerApi(allocationWebClient);
var balance = balances.getPrisonerBalance("AA123456").block(Duration.ofSeconds(10));
var request = new PrisonerBalanceAdjustmentDto(
    PrisonerBalanceAdjustmentDto.AdjustmentReasonType.GOVERNOR_ADJUSTMENT,
    "ABC1234", 1, null, null
);
```

Java callers supply all constructor arguments; Kotlin default arguments do not create
Java overloads. Generated DTOs are separate types from the API's server DTOs.

### Jackson compatibility

The pinned generator's infrastructure and optional `BalanceControllerApi(baseUrl)`
constructor use Jackson 2. Its Jackson 2 dependencies are therefore included in the
publication. Spring Boot 4 consumers can supply their existing WebClient with Jackson 3
codecs.
Jackson 2 and 3 have different databind packages and coexist here. No custom generator
templates or generated-source rewriting are used. The Spring Boot dependency platform
is published as dependency constraints, so review dependency alignment in your consumer.

### Error responses

HTTP 4xx/5xx responses are reactive errors represented by `WebClientResponseException`.
They retain status, headers and body. A structured 422 response can be decoded as follows:

```kotlin
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitallocationclient.model.ManualBalanceAdjustmentValidationErrorResponse

try {
  balances.adjustPrisonerVOBalance(prisonerId, request).block(Duration.ofSeconds(10))
} catch (error: WebClientResponseException) {
  if (error.statusCode.value() == 422) {
    val details = error.getResponseBodyAs(ManualBalanceAdjustmentValidationErrorResponse::class.java)
    // Handle details?.validationErrors according to your application's requirements.
  } else {
    throw error
  }
}
```

For a reactive flow use `onErrorResume`/`onErrorMap`. Transport failures and timeouts
are separate from HTTP error responses. Domain-specific exceptions are not generated.

There is no client-specific automated test suite in this stage. Verification is manual
in the consuming API. Remote publishing, immutable releases, automatic API startup/export
in CI, broader consumer compatibility and migrating a downstream service are future stages.
