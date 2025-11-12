package no.nav.toi.kandidatvarsel

import auth.obo.KandidatsokApiKlient
import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import no.nav.toi.kandidatvarsel.Rolle.*
import no.nav.toi.kandidatvarsel.altinnsms.AltinnVarsel
import no.nav.toi.kandidatvarsel.minside.Kanal
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

    /** Varsling er ferdigstilt*/
    FERDIGSTILT,

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
    KANDIDAT_INVITERT_TREFF(Mal.Companion.KandidatInvitertTreff),
    INVITERT_TREFF_KANDIDAT_ENDRET(Mal.Companion.InvitertTreffKandidatEndret),
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
    val eksternKanal: Kanal?,
)

fun Javalin.handleVarsler(dataSource: DataSource, kandidatsokApiKlient: KandidatsokApiKlient) {
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
        REKBIS_UTVIKLER,
        REKBIS_ARBEIDSGIVERRETTET
    )

    get(
        "/api/varsler/rekrutteringstreff/{rekrutteringstreffId}",
        { ctx ->
            val rekrutteringstreffId = ctx.pathParam("rekrutteringstreffId")
            val varsler = dataSource.transaction { tx ->
                MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
                    .map { it.toResponse() }
            }
            ctx.json(varsler)
        },
        REKBIS_UTVIKLER,
        REKBIS_ARBEIDSGIVERRETTET
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
                        avsenderReferanseId = stillingId,
                        mottakerFnr = fnr,
                        avsenderNavident = ctx.authenticatedUser().navident,
                    )
                        .insert(tx)
                    /* TODO: audit-logg? */
                }
            }
            ctx.status(HttpStatus.CREATED)
        },
        REKBIS_UTVIKLER,
        REKBIS_ARBEIDSGIVERRETTET

    )

    post(
        "/api/varsler/query",
        { ctx ->
            val queryRequestDto = ctx.bodyAsClass<QueryRequestDto>()
            val navident = ctx.authenticatedUser().navident
            val fnr = queryRequestDto.fnr
            var fikkTilgang = false
            try {
                val roller = ctx.authenticatedUser().roller

                if (roller.size == 1 && roller.first() == REKBIS_JOBBSØKERRETTET) {
                    kandidatsokApiKlient.verifiserKandidatTilgang(ctx, navident, fnr)
                }

                val varsler = dataSource.transaction { tx ->
                    val minsideVarsler = MinsideVarsel.hentVarslerForQuery(tx, queryRequestDto)
                        .map { it.toResponse() }
                    val altinnVarsler = AltinnVarsel.hentVarslerForQuery(tx, queryRequestDto)
                        .map { it.toResponse() }
                    minsideVarsler + altinnVarsler
                }
                fikkTilgang = true
                ctx.json(varsler)
            } finally {
                AuditLogg.logCefMessage(
                    navIdent = navident,
                    userid = fnr,
                    msg = "Hentet beskjeder om rekruttering sendt til bruker",
                    tilgang = fikkTilgang
                )
            }
        },
        REKBIS_UTVIKLER,
        REKBIS_JOBBSØKERRETTET,
        REKBIS_ARBEIDSGIVERRETTET,
    )
}

