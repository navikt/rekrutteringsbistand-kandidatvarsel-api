package no.nav.toi

import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import java.time.LocalDateTime

private const val endepunktBackfill = "/m2m-api/backfill"

data class BackfillRequest(
    val id: Int,
    val opprettet: LocalDateTime,
    val sendt: LocalDateTime?,
    val melding: String,
    val fnr: String,
    val stillingId: String,
    val navident: String,
    val status: BackfillStatus,
    val sistFeilet: LocalDateTime?
)

enum class BackfillStatus {
    SENDT, UNDER_UTSENDING, IKKE_SENDT, FEIL
}

@OpenApi(
    summary = "For at det gamle systemet skal overføre historisk data hit",
    operationId = endepunktBackfill,
    tags = [],
    responses = [OpenApiResponse("200")],
    path = endepunktBackfill,
    methods = [HttpMethod.POST]
)
fun Javalin.handleBackfill() {
    post(endepunktBackfill) { ctx ->
        val sms = ctx.bodyAsClass<BackfillRequest>()
        ctx.status(200)
    }
}
