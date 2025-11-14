package no.nav.toi.kandidatvarsel
import io.javalin.Javalin
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

data class InvitertTreffKandidatEndret(
    val smsTekst: String,
    val epostTittel: String,
    val epostHtmlBody: String
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

data class RekrutteringstreffMeldingsmal(
    val kandidatInvitertTreff: KandidatInvitertTreff,
    val invistertTreffKandidatEndret: InvitertTreffKandidatEndret
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

fun hentRekrutteringstreffMeldingsmal(): RekrutteringstreffMeldingsmal {
    val kandidatInvitertTreff = KandidatInvitertTreff
    val invitertTreffKandidatEndret = InvitertKandidatTreffEndret
    return RekrutteringstreffMeldingsmal(
        kandidatInvitertTreff = KandidatInvitertTreff(
            smsTekst = kandidatInvitertTreff.smsTekst(),
            epostTittel = kandidatInvitertTreff.epostTittel(),
            epostHtmlBody = kandidatInvitertTreff.epostHtmlBody()
        ),
        invistertTreffKandidatEndret = InvitertTreffKandidatEndret(
            smsTekst = InvitertKandidatTreffEndret.smsTekst(),
            epostTittel = InvitertKandidatTreffEndret.epostTittel(),
            epostHtmlBody = InvitertKandidatTreffEndret.epostHtmlBody()
        )
    )
}

fun hentMeldingsmal(): Meldingsmal {
    val stillingMeldingsmal = hentStillingMeldingsmal()
    return Meldingsmal(
        vurdertSomAktuell = stillingMeldingsmal.vurdertSomAktuell,
        passendeStilling = stillingMeldingsmal.passendeStilling,
        passendeJobbarrangement = stillingMeldingsmal.passendeJobbarrangement
    )
}

fun Javalin.handleMeldingsmal() {
    // Deprecated endpoint - bruk /api/meldingsmal/stilling for stilling-maler
    get(
        "/api/meldingsmal",
        { ctx ->
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