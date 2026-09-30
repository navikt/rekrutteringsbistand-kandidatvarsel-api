package no.nav.toi.kandidatvarsel
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.toi.kandidatvarsel.minside.*

data class VurdertSomAktuell(
    val smsTekst: String,
    val epostTittel: String,
    val epostHtmlBody: String
)

data class PassendeStilling(
    val smsTekst: String,
    val epostTittel: String,
    val epostHtmlBody: String
)

data class PassendeJobbarrangement(
    val smsTekst: String,
    val epostTittel: String,
    val epostHtmlBody: String
)

data class KandidatInvitertTreff(
    val smsTekst: String,
    val epostTittel: String,
    val epostHtmlBody: String
)

data class KandidatInvitertTreffEndret(
    val smsTekst: String,
    val epostTittel: String,
    val epostHtmlBody: String,
    val placeholder: String,
    /** Liste over mulige endringsfelt med kode og displayTekst som brukes i varsler */
    val endringsFelt: List<EndringsFeltDto>
)

/** Representerer et felt som kan endres på et rekrutteringstreff.
 *  displayTekst er det som vises i sms/email/minside.
 *  kode er identifikatoren som brukes i API-kall. */
data class EndringsFeltDto(
    val kode: String,
    val displayTekst: String
)

data class Meldingsmal(
    val vurdertSomAktuell: VurdertSomAktuell,
    val passendeStilling: PassendeStilling,
    val passendeJobbarrangement: PassendeJobbarrangement
)

data class StillingMeldingsmal(
    val vurdertSomAktuell: VurdertSomAktuell,
    val passendeStilling: PassendeStilling,
    val passendeJobbarrangement: PassendeJobbarrangement
)

/** WorkOp har egne maler. De har samme felter som treffmalene. */
data class RekrutteringstreffMeldingsmal(
    val kandidatInvitertTreff: KandidatInvitertTreff,
    val kandidatInvitertTreffEndret: KandidatInvitertTreffEndret,
    val kandidatInvitertWorkOp: KandidatInvitertTreff,
    val kandidatInvitertWorkOpEndret: KandidatInvitertTreffEndret,
)

fun hentStillingMeldingsmal(): StillingMeldingsmal {
    val vurdertSomAktuell = VurdertSomAktuell
    val passendeStilling = PassendeStilling
    val passendeJobbarrangement = PassendeJobbarrangement
    return StillingMeldingsmal(
        vurdertSomAktuell = VurdertSomAktuell(
            smsTekst = vurdertSomAktuell.smsTekst(),
            epostTittel = vurdertSomAktuell.epostTittel(),
            epostHtmlBody = vurdertSomAktuell.epostHtmlBody()
        ),
        passendeStilling = PassendeStilling(
            smsTekst = passendeStilling.smsTekst(),
            epostTittel = passendeStilling.epostTittel(),
            epostHtmlBody = passendeStilling.epostHtmlBody()
        ),
        passendeJobbarrangement = PassendeJobbarrangement(
            smsTekst = passendeJobbarrangement.smsTekst(),
            epostTittel = passendeJobbarrangement.epostTittel(),
            epostHtmlBody = passendeJobbarrangement.epostHtmlBody()
        )
    )
}

fun hentRekrutteringstreffMeldingsmal() = RekrutteringstreffMeldingsmal(
    kandidatInvitertTreff = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff.tilInvitertDto(),
    kandidatInvitertTreffEndret = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret.tilEndretDto(),
    kandidatInvitertWorkOp = KandidatInvitertWorkOp.tilInvitertDto(),
    kandidatInvitertWorkOpEndret = KandidatInvitertWorkOpEndret.tilEndretDto(),
)

private fun RekrutteringstreffMal.tilInvitertDto() = KandidatInvitertTreff(
    smsTekst = smsTekst(),
    epostTittel = epostTittel(),
    epostHtmlBody = epostHtmlBody(),
)

private fun EndretRekrutteringstreffMal.tilEndretDto() = KandidatInvitertTreffEndret(
    smsTekst = smsTekst(),
    epostTittel = epostTittel(),
    epostHtmlBody = epostHtmlBody(),
    placeholder = EndretRekrutteringstreffMal.PLACEHOLDER,
    endringsFelt = EndringFlettedata.entries.map { EndringsFeltDto(it.name, it.displayTekst) },
)

fun hentMeldingsmal(): Meldingsmal {
    val stillingMeldingsmal = hentStillingMeldingsmal()
    return Meldingsmal(
        vurdertSomAktuell = stillingMeldingsmal.vurdertSomAktuell,
        passendeStilling = stillingMeldingsmal.passendeStilling,
        passendeJobbarrangement = stillingMeldingsmal.passendeJobbarrangement
    )
}

fun JavalinDefaultRoutingApi.handleMeldingsmal() {
    get(
        "/api/meldingsmal",
        { ctx ->
            log.warn("Deprecated endpoint /api/meldingsmal kalles - bruk /api/meldingsmal/stilling i stedet")
            ctx.json(hentMeldingsmal())
        },
        Rolle.UNPROTECTED
    )
    
    get(
        "/api/meldingsmal/stilling",
        { ctx ->
            ctx.json(hentStillingMeldingsmal())
        },
        Rolle.UNPROTECTED
    )
    
    get(
        "/api/meldingsmal/rekrutteringstreff",
        { ctx ->
            ctx.json(hentRekrutteringstreffMeldingsmal())
        },
        Rolle.UNPROTECTED
    )
}