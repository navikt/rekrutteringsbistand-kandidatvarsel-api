package no.nav.toi.kandidatvarsel.minside

import no.nav.toi.kandidatvarsel.Stilling
import org.intellij.lang.annotations.Language

sealed interface Mal {
    val name: String
    fun minsideTekst(stilling: Stilling): String
    fun smsTekst(): String
    fun epostTittel(): String
    fun epostHtmlBody(): String

    companion object {
        fun valueOf(name: String) = when (name) {
            VurdertSomAktuell.name -> VurdertSomAktuell
            PassendeStilling.name -> PassendeStilling
            PassendeJobbarrangement.name -> PassendeJobbarrangement
            else -> throw IllegalArgumentException("Ukjent Mal: $name")
        }

        data object VurdertSomAktuell: Mal {
            override val name = "VURDERT_SOM_AKTUELL"
            override fun minsideTekst(stilling: Stilling) =
                "Vi har vurdert at kompetansen din kan passe til stillingen «${stilling.title}» hos «${stilling.businessName}». Se stillingen her."
            override fun smsTekst() =
                "Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på NAV for å se stillingen. Vennlig hilsen NAV"
            override fun epostTittel() =
                "Stilling som kan passe for deg?"
            override fun epostHtmlBody() =
                epostHtmlBodyTemplate("""
                    Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på NAV for å se stillingen.
                """.trimIndent())
        }

        data object PassendeStilling: Mal {
            override val name = "PASSENDE_STILLING"
            override fun minsideTekst(stilling: Stilling) =
                "Vi har funnet stillingen «${stilling.title}» hos «${stilling.businessName}» som kan passe deg. Interessert? Søk via lenka i annonsen."
            override fun smsTekst() =
                "Hei! Vi har funnet en stilling som kan passe deg. Logg inn på NAV for å se stillingen. Vennlig hilsen NAV"
            override fun epostTittel() =
                "Stilling som kan passe for deg?"
            override fun epostHtmlBody() =
                epostHtmlBodyTemplate("""
                    Vi har funnet en stilling som kanskje kan passe for deg. Logg inn på NAV for å se stillingen.
                """.trimIndent())
        }

        data object PassendeJobbarrangement: Mal {
            override val name = "PASSENDE_JOBBARRANGEMENT"
            override fun minsideTekst(stilling: Stilling) =
                "Vi har et jobbarrangement som kanskje passer for deg"
            override fun smsTekst() =
                "Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på NAV for å se arrangementet. Vennlig hilsen NAV"
            override fun epostTittel() =
                "Jobbarrangement"
            override fun epostHtmlBody() =
                epostHtmlBodyTemplate("""
                    Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på NAV for å se arrangementet.
                """.trimIndent())
        }

        private fun epostHtmlBodyTemplate(@Language("HTML") tekst: String) = """
            <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Hei!</p><p>$tekst</p><p>Vennlig hilsen</p><p>NAV</p></body></html>
        """.trimIndent()
    }
}