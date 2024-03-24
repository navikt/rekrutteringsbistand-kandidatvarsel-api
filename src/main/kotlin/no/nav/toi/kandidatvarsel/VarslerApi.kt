package no.nav.toi.kandidatvarsel

import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import no.nav.toi.kandidatvarsel.Rolle.*
import no.nav.toi.kandidatvarsel.altinnsms.AltinnVarsel
import no.nav.toi.kandidatvarsel.minside.Mal
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel
import java.time.LocalDateTime
import javax.sql.DataSource

enum class EksternStatusDto {
    /** Vi jobber med å sende ut eksternt varsel. Status er ikke avklart enda. */
    UNDER_UTSENDING,

    /** Vi har fått bekreftet at en SMS er sendt. */
    VELLYKKET_SMS,

    /** Vi har fått bekreftet at en e-post er sendt. */
    VELLYKKET_EPOST,

    /** Det skjedde en feil, og vi vil ikke prøve å sende varselet igjen. */
    FEIL,
}

enum class MinsideStatusDto {
    /** Det kommer ingen beskjed på min side, fordi varselet ble
     * opprettet før vi hadde minside-integrasjon. */
    IKKE_BESTILT,

    /** Vi jobber med å få opprettet beskjeden  på minside. */
    UNDER_UTSENDING,

    /** Minside har bekreftet av de har opprettet beskjeden. */
    OPPRETTET,

    /** Beskjeden er slettet og ikke lenger synlig for bruker eller saksbehandler. */
    SLETTET,
}

@Suppress("unused" /* deserialiseres */)
enum class MalDto(val mal: Mal) {
    VURDERT_SOM_AKTUELL(Mal.Companion.VurdertSomAktuell),
    PASSENDE_STILLING(Mal.Companion.PassendeStilling),
    PASSENDE_JOBBARRANGEMENT(Mal.Companion.PassendeJobbarrangement),
}

data class QueryRequestDto(
    val fnr: String,
)

data class NyeVarslerRequestDto(
    val fnr: List<String>,
    val mal: MalDto,
)

data class VarselResponseDto(
    val id: String,
    val opprettet: LocalDateTime,
    val stillingId: String,
    val mottakerFnr: String,
    val avsenderNavident: String,
    val minsideStatus: MinsideStatusDto,
    val eksternStatus: EksternStatusDto,
    val eksternFeilmelding: String?,
)

fun Javalin.handleVarsler(dataSource: DataSource, nyTilgangsstyring: Boolean) {
    get(
        "/api/varsler/stilling/{stillingId}",
        { ctx ->
            val stillingId = ctx.pathParam("stillingId")
            val varsler = dataSource.transaction { tx ->
                val minsideVarsler = MinsideVarsel.hentVarslerForStilling(tx, stillingId)
                    .map { it.toResponse() }
                val altinnVarsler = AltinnVarsel.hentVarslerForStilling(tx, stillingId)
                    .map { it.toResponse() }
                minsideVarsler + altinnVarsler
            }
            ctx.json(varsler)
        },
        *if (nyTilgangsstyring) arrayOf(
            REKBIS_UTVIKLER,
            REKBIS_ARBEIDSGIVERRETTET,
        ) else arrayOf(
            MODIA_GENERELL,
            MODIA_OPPFØLGING,
            REKBIS_UTVIKLER,
            REKBIS_JOBBSØKERRETTET,
            REKBIS_ARBEIDSGIVERRETTET,
        )
    )

    post(
        "/api/varsler/stilling/{stillingId}",
        { ctx ->
            val stillingId = ctx.pathParam("stillingId")
            val nyeVarslerRequestDto = ctx.bodyAsClass<NyeVarslerRequestDto>()
            dataSource.transaction { tx ->
                for (fnr in nyeVarslerRequestDto.fnr) {
                    MinsideVarsel.create(
                        mal = nyeVarslerRequestDto.mal.mal,
                        stillingId = stillingId,
                        mottakerFnr = fnr,
                        avsenderNavident = ctx.authenticatedUser().navident,
                    )
                        .insert(tx)
                    /* TODO: audit-logg? */
                }
            }
            ctx.status(HttpStatus.CREATED)
        },
        *if (nyTilgangsstyring) arrayOf(
            REKBIS_UTVIKLER,
            REKBIS_ARBEIDSGIVERRETTET,
        ) else arrayOf(
            MODIA_GENERELL,
            MODIA_OPPFØLGING,
            REKBIS_UTVIKLER,
            REKBIS_JOBBSØKERRETTET,
            REKBIS_ARBEIDSGIVERRETTET,
        )
    )

    post(
        "/api/varsler/query",
        { ctx ->
            val queryRequestDto = ctx.bodyAsClass<QueryRequestDto>()
            AuditLogg.logCefMessage(
                navIdent = ctx.authenticatedUser().navident,
                userid = queryRequestDto.fnr,
                msg = "Hentet beskjeder om rekruttering sendt til bruker",
            )
            val varsler = dataSource.transaction { tx ->
                val minsideVarsler = MinsideVarsel.hentVarslerForQuery(tx, queryRequestDto)
                    .map { it.toResponse() }
                val altinnVarsler = AltinnVarsel.hentVarslerForQuery(tx, queryRequestDto)
                    .map { it.toResponse() }
                minsideVarsler + altinnVarsler
            }
            ctx.json(varsler)
        },
        *if (nyTilgangsstyring) arrayOf(
            REKBIS_UTVIKLER,
            REKBIS_JOBBSØKERRETTET,
            REKBIS_ARBEIDSGIVERRETTET,
        ) else arrayOf(
            MODIA_GENERELL,
            MODIA_OPPFØLGING,
            REKBIS_UTVIKLER,
            REKBIS_JOBBSØKERRETTET,
            REKBIS_ARBEIDSGIVERRETTET,
        )
    )
}

