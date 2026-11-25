package no.nav.toi.kandidatvarsel

import auth.obo.KandidatsokApiKlient
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.javalin.json.JavalinJackson3
import io.javalin.validation.ValidationException
import org.flywaydb.core.api.output.MigrateResult
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.util.concurrent.atomic.AtomicReference

private val noClassLogger = noClassLogger()

fun startJavalin(
    azureAdConfig: AzureAdConfig,
    dataSource: HikariDataSource,
    migrateResult: AtomicReference<MigrateResult>,
    kandidatsokApiKlient: KandidatsokApiKlient,
    isRapidRunning: () -> Boolean,
    port: Int = 8080,
): Javalin = Javalin
    .create {
        val objectMapper = jacksonMapperBuilder()
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()
        it.jsonMapper(JavalinJackson3(objectMapper))

        it.startup.showJavalinBanner = false

        with(it.routes) {
            azureAdAuthentication(azureAdConfig)
            handleHealth(dataSource, migrateResult, isRapidRunning)
            handleVarsler(dataSource, kandidatsokApiKlient)
            handleMeldingsmal()

            exception(ValidationException::class.java) { e, ctx ->
                noClassLogger.info("Returnerer 400 Bad Request på grunn av: ${e.errors}", e)
                ctx.json(e.errors).status(HttpStatus.BAD_REQUEST)
            }
            exception(Exception::class.java) { e, ctx ->
                noClassLogger.error("uhåndtert exception i javalin: {}", e.message, e)
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }

        it.requestLogger.http { ctx, ms ->
            if (ctx.path().startsWith("/internal/")) return@http
            noClassLogger.info("${ctx.method()} ${ctx.path()} -> ${ctx.status()} (${ms}ms)")
        }
    }
    .apply {
        start(port)
    }
