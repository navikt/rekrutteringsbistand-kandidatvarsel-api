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

data class RekrutteringstreffMeldingsmal(
    val kandidatInvitertTreff: KandidatInvitertTreff,
    val kandidatInvitertTreffEndret: KandidatInvitertTreffEndret
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
    val kandidatInvitertTreff = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff
    val kandidatInvitertTreffEndret = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret
    return RekrutteringstreffMeldingsmal(
        kandidatInvitertTreff = KandidatInvitertTreff(
            smsTekst = kandidatInvitertTreff.smsTekst(),
            epostTittel = kandidatInvitertTreff.epostTittel(),
            epostHtmlBody = kandidatInvitertTreff.epostHtmlBody()
        ),
        kandidatInvitertTreffEndret = KandidatInvitertTreffEndret(
            smsTekst = kandidatInvitertTreffEndret.smsTekst(),
            epostTittel = kandidatInvitertTreffEndret.epostTittel(),
            epostHtmlBody = kandidatInvitertTreffEndret.epostHtmlBody(),
            placeholder = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret.PLACEHOLDER,
            endringsFelt = MalParameter.entries.map { EndringsFeltDto(it.name, it.displayTekst) }
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