package no.nav.toi.kandidatvarsel.minside

import org.intellij.lang.annotations.Language

enum class VarselType {
    STILLING,
    REKRUTTERINGSTREFF
}

enum class EndringFlettedata(val displayTekst: String) {
    NAVN("navn"),
    TIDSPUNKT("tidspunkt"),
    SVARFRIST("svarfrist"),
    STED("sted"),
    INTRODUKSJON("introduksjon")
}

sealed interface Mal {
    val name: String
    val varselType: VarselType

    fun smsTekst(): String
    fun epostTittel(): String
    fun epostHtmlBody(): String

    fun lenkeurl(avsenderReferanseId: String, isProd: Boolean): String {
        return when (varselType) {
            VarselType.STILLING -> "https://www.nav.no/arbeid/stilling/$avsenderReferanseId"
            VarselType.REKRUTTERINGSTREFF -> {
                val domain = if (isProd)
                    "www.nav.no"
                else
                    "rekrutteringstreff-bruker.intern.dev.nav.no"

                return "https://$domain/rekrutteringstreff/$avsenderReferanseId"
            }
        }
    }

    fun brukerRapid(): Boolean
}

sealed interface StillingMal : Mal {
    override val varselType: VarselType get() = VarselType.STILLING
    fun minsideTekst(tittel: String, arbeidsgiver: String): String
    override fun brukerRapid() = false
}

sealed interface RekrutteringstreffMal : Mal {
    override val varselType: VarselType get() = VarselType.REKRUTTERINGSTREFF
    fun minsideTekst(): String
    override fun brukerRapid() = true
}

object Maler {
    fun valueOf(name: String): Mal {
        // Håndterer gammelt kolonseparert format fra dev-miljø (f.eks. "KANDIDAT_INVITERT_TREFF_ENDRET:SVARFRIST,STED")
        val malNavn = name.substringBefore(":")
        
        return when (malNavn) {
            VurdertSomAktuell.name -> VurdertSomAktuell
            PassendeStilling.name -> PassendeStilling
            PassendeJobbarrangement.name -> PassendeJobbarrangement
            KandidatInvitertTreff.name -> KandidatInvitertTreff
            KandidatInvitertTreffEndret.name -> KandidatInvitertTreffEndret
            else -> throw IllegalArgumentException("Ukjent Mal: $name")
        }
    }

    fun malerForVarselType(varselType: VarselType): List<String> = when (varselType) {
        VarselType.STILLING -> listOf(
            VurdertSomAktuell.name,
            PassendeStilling.name,
            PassendeJobbarrangement.name
        )

        VarselType.REKRUTTERINGSTREFF -> listOf(
            KandidatInvitertTreff.name,
            KandidatInvitertTreffEndret.name
        )
    }

    fun epostHtmlBodyTemplate(@Language("HTML") tekst: String) = """
        <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Hei!</p><p>$tekst</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
    """.trimIndent()
}

data object VurdertSomAktuell : StillingMal {
    override val name = "VURDERT_SOM_AKTUELL"

    override fun minsideTekst(tittel: String, arbeidsgiver: String) =
        "Vi har vurdert at kompetansen din kan passe til stillingen «${tittel}» hos «${arbeidsgiver}». Se stillingen her."

    override fun smsTekst() =
        "Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav"

    override fun epostTittel() =
        "Stilling som kan passe for deg?"

    override fun epostHtmlBody() =
        Maler.epostHtmlBodyTemplate(
            """
                    Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen.
                """.trimIndent()
        )
}

data object PassendeStilling : StillingMal {
    override val name = "PASSENDE_STILLING"

    override fun minsideTekst(tittel: String, arbeidsgiver: String) =
        "Vi har funnet stillingen «${tittel}» hos «${arbeidsgiver}» som kan passe deg. Interessert? Søk via lenka i annonsen."

    override fun smsTekst() =
        "Hei! Vi har funnet en stilling som kan passe deg. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav"

    override fun epostTittel() =
        "Stilling som kan passe for deg?"

    override fun epostHtmlBody() =
        Maler.epostHtmlBodyTemplate(
            """
                    Vi har funnet en stilling som kanskje kan passe for deg. Logg inn på Nav for å se stillingen.
                """.trimIndent()
        )
}

data object PassendeJobbarrangement : StillingMal {
    override val name = "PASSENDE_JOBBARRANGEMENT"

    override fun minsideTekst(tittel: String, arbeidsgiver: String) =
        "Vi har et jobbarrangement som kanskje passer for deg"

    override fun smsTekst() =
        "Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet. Vennlig hilsen Nav"

    override fun epostTittel() =
        "Jobbarrangement"

    override fun epostHtmlBody() =
        Maler.epostHtmlBodyTemplate(
            """
                    Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet.
                """.trimIndent()
        )
}

data object KandidatInvitertTreff : RekrutteringstreffMal {
    override val name = "KANDIDAT_INVITERT_TREFF"

    override fun minsideTekst() =
        "Du er invitert til et treff der du kan møte arbeidsgivere."

    override fun smsTekst() =
        "Hei! Du er invitert til et treff der du kan møte arbeidsgivere. Logg inn på Nav for å melde deg på. Vennlig hilsen Nav"

    override fun epostTittel() =
        "Invitasjon til å treffe arbeidsgivere"

    override fun epostHtmlBody() =
        """
        <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Hei! Du er invitert til et treff der du kan møte arbeidsgivere. Logg inn på Nav for å melde deg på.</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
        """.trimIndent()
}

data object KandidatInvitertTreffEndret : RekrutteringstreffMal {
    override val name = "KANDIDAT_INVITERT_TREFF_ENDRET"
    
    const val PLACEHOLDER = "{{ENDRINGER}}"

    override fun minsideTekst() =
        "Det har skjedd endringer i $PLACEHOLDER knyttet til et treff med arbeidsgivere som du er invitert til."

    override fun smsTekst() =
        "Det har skjedd endringer på et treff med arbeidsgivere som du er invitert til:\n\n$PLACEHOLDER\n\nLogg inn på Nav for mer informasjon.\n\nVennlig hilsen Nav"

    override fun epostTittel() =
        "Endringer på treff du er invitert til"

    override fun epostHtmlBody() =
        """
        <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Det har skjedd endringer på et treff med arbeidsgivere som du er invitert til:</p><p>$PLACEHOLDER</p><p>Logg inn på Nav for mer informasjon.</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
        """.trimIndent()

    fun minsideTekst(endringsTekster: List<String>) =
        minsideTekst().replace(PLACEHOLDER, formaterEndringer(endringsTekster))
    
    fun smsTekst(endringsTekster: List<String>) =
        smsTekst().replace(PLACEHOLDER, formaterEndringer(endringsTekster))
    
    fun epostHtmlBody(endringsTekster: List<String>) =
        epostHtmlBody().replace(PLACEHOLDER, formaterEndringer(endringsTekster))
    
    /** Formaterer liste med endringsTekster til lesbar norsk tekst.
     *  F.eks. ["tidspunkt", "sted"] -> "tidspunkt og sted" 
     *  F.eks. ["navn", "tidspunkt", "sted"] -> "navn, tidspunkt og sted" */
    private fun formaterEndringer(endringsTekster: List<String>): String {
        if (endringsTekster.isEmpty()) {
            return ""
        }
        return when (endringsTekster.size) {
            1 -> endringsTekster.first()
            else -> endringsTekster.dropLast(1).joinToString(", ") + " og " + endringsTekster.last()
        }
    }
}