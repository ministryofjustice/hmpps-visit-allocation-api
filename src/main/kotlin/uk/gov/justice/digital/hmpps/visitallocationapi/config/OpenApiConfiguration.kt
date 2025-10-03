package uk.gov.justice.digital.hmpps.visitallocationapi.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.expression.BeanFactoryResolver
import org.springframework.expression.spel.SpelEvaluationException
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.method.HandlerMethod

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version

  @Autowired
  private lateinit var context: ApplicationContext

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(
      listOf(
        Server().url("https://hmpps-visit-allocation-api-dev.prison.service.justice.gov.uk").description("Development"),
        Server().url("https://hmpps-visit-allocation-api-staging.prison.service.justice.gov.uk").description("Staging"),
        Server().url("https://hmpps-visit-allocation-api-preprod.prison.service.justice.gov.uk").description("Pre-Production"),
        Server().url("https://hmpps-visit-allocation-api.prison.service.justice.gov.uk").description("Production"),
        Server().url("http://localhost:8079").description("Local"),
      ),
    )
    .info(
      Info().title("HMPPS Visit Allocation API").version(version)
        .description(
          """
            |API for managing a prisoner visit order balance on DPS.
            |
            |## Authentication
            |
            |This API uses OAuth2 with JWTs. You will need to pass the JWT in the `Authorization` header using the `Bearer` scheme.
            |
            |## Authorisation
            |
            |The API uses roles to control access to the endpoints. The roles required for each endpoint are documented in the endpoint descriptions.
            |Services looking to integrate with the API should request a new role be created with the following syntax:
            | ROLE_VISIT_ALLOCATION_API__<SERVICE_NAME>
            | 
            |Pre-existing roles:
            | 1. ROLE_VISIT_ALLOCATION_API__NOMIS_API - [NOMIS use only] - Grants NOMIS read/write access to the API (syncing / retrieving balances).
            | 2. ROLE_VISIT_ALLOCATION_API__ADMIN     - [Admin use only] - Grants ability to action special requests via the admin controller.

            |
          """.trimMargin(),
        )
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk")),
    )
    .components(
      Components().addSecuritySchemes(
        "visit-allocation-api-nomis-role",
        SecurityScheme().addBearerJwtRequirement(ROLE_VISIT_ALLOCATION_API__NOMIS_API),
      )
        .addSecuritySchemes(
          "visit-allocation-api-admin-role",
          SecurityScheme().addBearerJwtRequirement(ROLE_VISIT_ALLOCATION_API__ADMIN),
        )
    )
    .addSecurityItem(SecurityRequirement().addList("visit-allocation-api-nomis-role", listOf("read", "write")))
    .addSecurityItem(SecurityRequirement().addList("visit-allocation-api-admin-role", listOf("read", "write")))

  @Bean
  fun preAuthorizeCustomizer(): OperationCustomizer = OperationCustomizer { operation: Operation, handlerMethod: HandlerMethod ->
    handlerMethod.preAuthorizeForMethodOrClass()?.let {
      val preAuthExp = SpelExpressionParser().parseExpression(it)
      val evalContext = StandardEvaluationContext()
      evalContext.beanResolver = BeanFactoryResolver(context)
      evalContext.setRootObject(
        object {
          fun hasRole(role: String) = listOf(role)
          fun hasAnyRole(vararg roles: String) = roles.toList()
        },
      )

      val roles = try {
        (preAuthExp.getValue(evalContext) as List<*>).filterIsInstance<String>()
      } catch (e: SpelEvaluationException) {
        emptyList()
      }
      if (roles.isNotEmpty()) {
        operation.description = "${operation.description ?: ""}\n\n" + "Requires one of the following roles:\n" + roles.joinToString(prefix = "* ", separator = "\n* ")
      }
    }

    operation
  }

  private fun HandlerMethod.preAuthorizeForMethodOrClass() = getMethodAnnotation(PreAuthorize::class.java)?.value
    ?: beanType.getAnnotation(PreAuthorize::class.java)?.value

  private fun SecurityScheme.addBearerJwtRequirement(role: String): SecurityScheme = type(SecurityScheme.Type.HTTP)
    .scheme("bearer")
    .bearerFormat("JWT")
    .`in`(SecurityScheme.In.HEADER)
    .name("Authorization")
    .description("A HMPPS Auth access token with the `$role` role.")
}
