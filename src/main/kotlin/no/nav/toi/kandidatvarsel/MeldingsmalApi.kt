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

data class Meldingsmal(
    val vurdertSomAktuell: VurdertSomAktuell,
    val passendeStilling: PassendeStilling,
    val passendeJobbarrangement: PassendeJobbarrangement
 )

fun hentMeldingsmal(): Meldingsmal {
    val vurdertSomAktuell = Mal.Companion.VurdertSomAktuell
    val passendeStilling = Mal.Companion.PassendeStilling
    val passendeJobbarrangement = Mal.Companion.PassendeJobbarrangement
    return Meldingsmal(
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

fun Javalin.handleMeldingsmal() {
    get(
        "/api/meldingsmal",
        { ctx ->
            ctx.json(hentMeldingsmal())
        },
        Rolle.UNPROTECTED
    )
}