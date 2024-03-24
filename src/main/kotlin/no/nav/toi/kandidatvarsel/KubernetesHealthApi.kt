package no.nav.toi.kandidatvarsel

import io.javalin.Javalin
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import no.nav.toi.kandidatvarsel.Rolle.UNPROTECTED
import org.flywaydb.core.api.output.MigrateResult
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

fun Javalin.handleHealth(dataSource: DataSource, migrationResult: AtomicReference<MigrateResult>) {
    fun checks(checks: Map<String, () -> Boolean>) = Handler { ctx ->
        val checkOutcomes = checks.mapValues { it.value() }
        val httpStatus = if (checkOutcomes.all { it.value }) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
        ctx.status(httpStatus).json(checkOutcomes)
    }

    val isReadyChecks = mapOf(
        "database" to { dataSource.isReady() },
        "migration" to { migrationResult.get()?.success == true }
    )

    val isAliveChecks = mapOf(
        "migration" to { migrationResult.get()?.success != false }
    )

    get("/internal/ready", checks(isReadyChecks), UNPROTECTED)
    get("/internal/alive", checks(isAliveChecks), UNPROTECTED)
}