package no.nav.toi.kandidatvarsel

import com.github.navikt.tbd_libs.rapids_and_rivers.KafkaRapid
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
    kafkaRapid: KafkaRapid
) {
    fun checks(checks: Map<String, () -> Boolean>) = Handler { ctx ->
        val checkOutcomes = checks.mapValues { it.value() }
        val httpStatus = if (checkOutcomes.all { it.value }) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
        ctx.status(httpStatus).json(checkOutcomes)
    }

    val isReadyChecks = buildMap {
        put("database") { dataSource.isReady() }
        put("migration") { migrationResult.get()?.success == true }
        put("rapid") { kafkaRapid.isRunning() }
    }

    val isAliveChecks = buildMap {
        put("migration") { migrationResult.get()?.success != false }
        put("rapid") { kafkaRapid.isRunning() }
    }

    get("/internal/ready", checks(isReadyChecks), UNPROTECTED)
    get("/internal/alive", checks(isAliveChecks), UNPROTECTED)
}