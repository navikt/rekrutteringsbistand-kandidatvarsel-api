package no.nav.toi.kandidatvarsel

import io.javalin.Javalin
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import no.nav.toi.kandidatvarsel.Rolle.UNPROTECTED
import org.flywaydb.core.api.output.MigrateResult
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

fun Javalin.handleHealth(
    dataSource: DataSource,
    migrationResult: AtomicReference<MigrateResult>,
    rapidIsAlive: (() -> Boolean)? = null
) {
    fun checks(checks: Map<String, () -> Boolean>) = Handler { ctx ->
        val checkOutcomes = checks.mapValues { it.value() }
        val httpStatus = if (checkOutcomes.all { it.value }) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
        ctx.status(httpStatus).json(checkOutcomes)
    }

    val isReadyChecks = buildMap {
        put("database") { dataSource.isReady() }
        put("migration") { migrationResult.get()?.success == true }
        rapidIsAlive?.let { put("rapid") { it() } }
    }

    val isAliveChecks = buildMap {
        put("migration") { migrationResult.get()?.success != false }
        rapidIsAlive?.let { put("rapid") { it() } }
    }

    get("/internal/ready", checks(isReadyChecks), UNPROTECTED)
    get("/internal/alive", checks(isAliveChecks), UNPROTECTED)
}