package no.nav.toi.kandidatvarsel.altinnsms

import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import no.nav.toi.kandidatvarsel.Rolle
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

data class BackfillRequest(
    val frontendId: String,
    val opprettet: LocalDateTime,
    val stillingId: String,
    val melding: String,
    val fnr: String,
    val status: String,
    val navIdent: String,
)

fun Javalin.handleBackfill(dataSource: DataSource) {
    val jdbcClient = JdbcClient.create(dataSource)

    post("/api/backfill", { ctx ->
        val backfillRequest = ctx.bodyAsClass<List<BackfillRequest>>()
        for (req in backfillRequest) {
            AltinnVarsel.storeBackfill(jdbcClient, req)
        }
        ctx.status(201)
    }, Rolle.MASKIN_TIL_MASKIN)
}

