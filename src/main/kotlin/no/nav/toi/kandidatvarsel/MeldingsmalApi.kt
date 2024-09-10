package no.nav.toi.kandidatvarsel
import io.javalin.Javalin
import no.nav.toi.kandidatvarsel.minside.Mal

data class Meldingsmal(
    val vurdertSomAktuellSmsTekst: String,
    val vurdertSomAktuellEpostTittel: String,
    val vurdertSomAktuellEpostHtmlBody: String,
    val passendeStillingSmsTekst: String,
    val passendeStillingEpostTittel: String,
    val passendeStillingEpostHtmlBody: String,
    val passendeJobbarrangementSmsTekst: String,
    val passendeJobbarangementEpostTittel: String,
    val passendeJobbarrangementEpostHtmlBody: String
 )

fun hentMeldingsmal(): Meldingsmal {
    val vurdertSomAktuell = Mal.Companion.VurdertSomAktuell
    val passendeStilling = Mal.Companion.PassendeStilling
    val passendeJobbarrangement = Mal.Companion.PassendeJobbarrangement
    return Meldingsmal(
        vurdertSomAktuellSmsTekst = vurdertSomAktuell.smsTekst(),
        vurdertSomAktuellEpostTittel = vurdertSomAktuell.epostTittel(),
        vurdertSomAktuellEpostHtmlBody = vurdertSomAktuell.epostHtmlBody(),
        passendeStillingSmsTekst = passendeStilling.smsTekst(),
        passendeStillingEpostTittel = passendeStilling.epostTittel(),
        passendeStillingEpostHtmlBody = passendeStilling.epostHtmlBody(),
        passendeJobbarrangementSmsTekst = passendeJobbarrangement.smsTekst(),
        passendeJobbarangementEpostTittel = passendeJobbarrangement.epostTittel(),
        passendeJobbarrangementEpostHtmlBody = passendeJobbarrangement.epostHtmlBody()
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
}