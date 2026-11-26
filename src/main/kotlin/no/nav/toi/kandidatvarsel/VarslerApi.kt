package no.nav.toi.kandidatvarsel

import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.toi.kandidatvarsel.Rolle.*
import no.nav.toi.kandidatvarsel.altinnsms.AltinnVarsel
import no.nav.toi.kandidatvarsel.minside.*
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
    VURDERT_SOM_AKTUELL(VurdertSomAktuell),
    PASSENDE_STILLING(PassendeStilling),
    DEL_CV(DelCv),
    PASSENDE_JOBBARRANGEMENT(PassendeJobbarrangement),
    KANDIDAT_INVITERT_TREFF(KandidatInvitertTreff),
    KANDIDAT_INVITERT_TREFF_ENDRET(KandidatInvitertTreffEndret),
}

data class NyeVarslerRequestDto(
    val fnr: List<String>,
    val mal: MalDto,
)

data class VarselResponseDto(
    val id: String,
    val opprettet: LocalDateTime,

    // TODO: Dette bør hete avsenderReferanseId når vi bytter feltnavn til det i databasen. Feltet kan også inneholde id til rekrutteirngstreff nå.
    val stillingId: String,
    
    val mottakerFnr: String,
    val avsenderNavident: String,
    val minsideStatus: MinsideStatusDto,
    val eksternStatus: EksternStatusDto,
    val eksternFeilmelding: String?,
    val eksternKanal: Kanal?,
)

fun JavalinDefaultRoutingApi.handleVarsler(dataSource: DataSource) {
    get(
        "/api/varsler/stilling/{stillingId}",
        { ctx ->
            val stillingId = ctx.pathParam("stillingId")
            log.info("Mottok request for å hente varsler for $stillingId")
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

    post(
        "/api/varsler/stilling/{stillingId}",
        { ctx ->
            val stillingId = ctx.pathParam("stillingId")
            log.info("Mottok request for å lage varsel for stilling $stillingId")
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
}

