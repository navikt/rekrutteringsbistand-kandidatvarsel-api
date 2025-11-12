package no.nav.toi.kandidatvarsel.minside

import no.nav.toi.kandidatvarsel.Stilling
import org.intellij.lang.annotations.Language

sealed interface Mal {
    val name: String
    fun minsideTekst(tittel: String?, arbeidsgiver: String?): String
    fun smsTekst(): String
    fun epostTittel(): String
    fun epostHtmlBody(): String

    companion object {
        fun valueOf(name: String) = when (name) {
            VurdertSomAktuell.name -> VurdertSomAktuell
            PassendeStilling.name -> PassendeStilling
            PassendeJobbarrangement.name -> PassendeJobbarrangement
            KandidatInvitertTreff.name -> KandidatInvitertTreff
            InvitertTreffKandidatEndret.name -> InvitertTreffKandidatEndret
            else -> throw IllegalArgumentException("Ukjent Mal: $name")
        }

        data object VurdertSomAktuell: Mal {
            override val name = "VURDERT_SOM_AKTUELL"
            override fun minsideTekst(tittel: String?, arbeidsgiver: String?) =
                "Vi har vurdert at kompetansen din kan passe til stillingen «${tittel}» hos «${arbeidsgiver}». Se stillingen her."
            override fun smsTekst() =
                "Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav"
            override fun epostTittel() =
                "Stilling som kan passe for deg?"
            override fun epostHtmlBody() =
                epostHtmlBodyTemplate("""
                    Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen.
                """.trimIndent())
        }

        data object PassendeStilling: Mal {
            override val name = "PASSENDE_STILLING"
            override fun minsideTekst(tittel: String?, arbeidsgiver: String?) =
                "Vi har funnet stillingen «${tittel}» hos «${arbeidsgiver}» som kan passe deg. Interessert? Søk via lenka i annonsen."
            override fun smsTekst() =
                "Hei! Vi har funnet en stilling som kan passe deg. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav"
            override fun epostTittel() =
                "Stilling som kan passe for deg?"
            override fun epostHtmlBody() =
                epostHtmlBodyTemplate("""
                    Vi har funnet en stilling som kanskje kan passe for deg. Logg inn på Nav for å se stillingen.
                """.trimIndent())
        }

        data object PassendeJobbarrangement: Mal {
            override val name = "PASSENDE_JOBBARRANGEMENT"
            override fun minsideTekst(tittel: String?, arbeidsgiver: String?) =
                "Vi har et jobbarrangement som kanskje passer for deg"
            override fun smsTekst() =
                "Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet. Vennlig hilsen Nav"
            override fun epostTittel() =
                "Jobbarrangement"
            override fun epostHtmlBody() =
                epostHtmlBodyTemplate("""
                    Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet.
                """.trimIndent())
        }

        data object KandidatInvitertTreff: Mal {
            override val name = "KANDIDAT_INVITERT_TREFF"
            override fun minsideTekst(tittel: String?, arbeidsgiver: String?) =
                "Du er invitert til et treff med arbeidsgivere. Du kan melde deg på inne på minside hos Nav."
            override fun smsTekst() =
                "Hei! Du er invitert til et treff med arbeidsgivere. Logg inn på Nav for å melde deg på. Vennlig hilsen Nav"
            override fun epostTittel() =
                "Du er invitert til et treff"
            override fun epostHtmlBody() =
                epostHtmlBodyTemplate("""
                    Du er invitert til et treff med arbeidsgivere. Logg inn på Nav for å melde deg på.
                """.trimIndent())
        }

        data object InvitertTreffKandidatEndret: Mal {
            override val name = "INVITERT_TREFF_KANDIDAT_ENDRET"
            override fun minsideTekst(tittel: String?, arbeidsgiver: String?) =
                if (tittel != null) "Det har skjedd endringer knyttet til treffet med arbeidsgivere: «${tittel}». Se mer her."
                else "Det har skjedd endringer knyttet til et treff med arbeidsgivere som du er invitert til. Se mer her."
            override fun smsTekst() =
                "Hei! Det har skjedd endringer på et treff med arbeidsgivere du er invitert til. Logg inn på Nav for mer informasjon. Vennlig hilsen Nav"
            override fun epostTittel() =
                "Endringer på treff du er invitert til"
            override fun epostHtmlBody() =
                epostHtmlBodyTemplate("""
                    Det har skjedd endringer på et treff med arbeidsgivere du er invitert til. Logg inn på Nav for mer informasjon.
                """.trimIndent())
        }

        fun epostHtmlBodyTemplate(@Language("HTML") tekst: String) = """
            <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Hei!</p><p>$tekst</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
        """.trimIndent()
    }
}