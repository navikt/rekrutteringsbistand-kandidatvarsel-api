package no.nav.toi.kandidatvarsel
import io.javalin.Javalin
import no.nav.toi.kandidatvarsel.minside.Mal

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
    val vurdertSomAktuell = Mal.Companion.VurdertSomAktuell
    val passendeStilling = Mal.Companion.PassendeStilling
    val passendeJobbarrangement = Mal.Companion.PassendeJobbarrangement
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
    val kandidatInvitertTreff = Mal.Companion.KandidatInvitertTreff
    val invistertTreffKandidatEndret = Mal.Companion.InvitertTreffKandidatEndret
    return RekrutteringstreffMeldingsmal(
        kandidatInvitertTreff = KandidatInvitertTreff(
            smsTekst = kandidatInvitertTreff.smsTekst(),
            epostTittel = kandidatInvitertTreff.epostTittel(),
            epostHtmlBody = kandidatInvitertTreff.epostHtmlBody()
        ),
        invistertTreffKandidatEndret = InvitertTreffKandidatEndret(
            smsTekst = invistertTreffKandidatEndret.smsTekst(),
            epostTittel = invistertTreffKandidatEndret.epostTittel(),
            epostHtmlBody = invistertTreffKandidatEndret.epostHtmlBody()
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