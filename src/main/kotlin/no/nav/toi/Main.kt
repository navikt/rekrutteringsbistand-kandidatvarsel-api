package no.nav.toi

import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.OpenApiPluginConfiguration
import io.javalin.openapi.plugin.swagger.SwaggerConfiguration
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import io.javalin.validation.ValidationException
import no.nav.toi.kuberneteshealth.handleHealth
import no.nav.toi.me.handleMe
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.*


/*
    Oppsett av applikasjon, som kan kjøres av både tester og main-metode.
 */
class App(
    private val port: Int = 8080,
    private val authenticationConfigurations: List<AuthenticationConfiguration>,
    private val rolleUuidSpesifikasjon: RolleUuidSpesifikasjon,
) : Closeable {

    lateinit var javalin: Javalin

    fun configureOpenApi(config: JavalinConfig) {
        val openApiConfiguration = OpenApiPluginConfiguration().apply {
            withDefinitionConfiguration { _, definition ->
                definition.apply {
                    withOpenApiInfo {
                        it.title = "Kandidatsøk API"
                    }
                }
            }
        }
        config.plugins.register(OpenApiPlugin(openApiConfiguration))
        config.plugins.register(SwaggerPlugin(SwaggerConfiguration().apply {
            this.validatorUrl = null
        }
        ))
    }


    fun start() {
        javalin = Javalin.create { config ->
            configureOpenApi(config)
        }

        javalin.handleHealth()
        javalin.handleMe()


        javalin.azureAdAuthentication(
            path = "/api/*",
            authenticationConfigurations = authenticationConfigurations,
            rolleUuidSpesifikasjon = rolleUuidSpesifikasjon,
        )

        val app = javalin.start(port)
        app.exception(ValidationException::class.java) { e, ctx ->
            log.info("Returnerer 400 Bad Request på grunn av: ${e.errors}", e)
            ctx.json(e.errors).status(BAD_REQUEST)
        }
        app.exception(Exception::class.java) { e, ctx ->
            log.error(e.message, e)
            ctx.status(INTERNAL_SERVER_ERROR)
        }
    }

    override fun close() {
        javalin.close()
    }
}

private val fakedingsAuthenticationConfiguration = AuthenticationConfiguration(
    jwksUri = "https://fakedings.intern.dev.nav.no/fake/jwks",
    issuer = "https://fakedings.intern.dev.nav.no/fake/aad",
    audience = "dev-gcp:toi:rekrutteringsbistand-kandidatvarsel-api",
)

fun main() {
    App(
        authenticationConfigurations = listOfNotNull(
            AuthenticationConfiguration(
                audience = getenvOrThrow("AZURE_APP_CLIENT_ID"),
                issuer = getenvOrThrow("AZURE_OPENID_CONFIG_ISSUER"),
                jwksUri  = getenvOrThrow("AZURE_OPENID_CONFIG_JWKS_URI"),
            ),
            if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp")
                fakedingsAuthenticationConfiguration
            else
                null
        ),
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            modiaGenerell = UUID.fromString(getenvOrThrow("MODIA_GENERELL_GRUPPE")),
            modiaOppfølging = UUID.fromString(getenvOrThrow("MODIA_OPPFOLGING_GRUPPE")),
        ),
    ).start()
}


val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

private fun getenvOrThrow(name: String): String = System.getenv(name) ?: throw IllegalStateException("Mangler miljøvariabel '$name'")