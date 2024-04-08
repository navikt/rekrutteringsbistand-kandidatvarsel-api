package no.nav.toi.kandidatvarsel

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.javalin.json.JavalinJackson
import io.javalin.validation.ValidationException
import no.nav.toi.kandidatvarsel.altinnsms.handleBackfill
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

fun startJavalin(
    azureAdConfig: AzureAdConfig,
    dataSource: HikariDataSource,
    migrateResult: AtomicReference<MigrateResult>,
    port: Int = 8080,
    nyTilgangsstyring: Boolean,
): Javalin = Javalin
    .create {
        val objectMapper = jacksonObjectMapper().apply {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            registerModule(JavaTimeModule())
        }
        it.jsonMapper(JavalinJackson(objectMapper))

        it.showJavalinBanner = false

        val log = LoggerFactory.getLogger("no.nav.toi.kandidatvarsel.Javalin")!!
        it.requestLogger.http { ctx, ms ->
            if (ctx.path().startsWith("/internal/")) return@http
            log.info("${ctx.method()} ${ctx.path()} -> ${ctx.status()} (${ms}ms)")
        }
    }
    .apply {
        azureAdAuthentication(azureAdConfig)
        handleHealth(dataSource, migrateResult)
        handleBackfill(dataSource)
        handleVarsler(dataSource, nyTilgangsstyring)

        exception(ValidationException::class.java) { e, ctx ->
            log.info("Returnerer 400 Bad Request på grunn av: ${e.errors}", e)
            ctx.json(e.errors).status(HttpStatus.BAD_REQUEST)
        }

        exception(Exception::class.java) { e, ctx ->
            log.error("uhåndtert exception i javalin: {}", e.message, e)
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
        }
        start(port)
    }

