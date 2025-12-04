package no.nav.toi.kandidatvarsel.minside

import org.intellij.lang.annotations.Language

enum class VarselType {
    STILLING,
    REKRUTTERINGSTREFF
}

enum class MalParameter(val displayTekst: String) {
    TITTEL("tittel"),
    TIDSPUNKT("tidspunkt"),
    SVARFRIST("svarfrist"),
    STED("sted"),
    INNHOLD("innhold");

    companion object {
        fun fromString(value: String): MalParameter = 
            entries.find { it.name == value.uppercase() }
                ?: throw IllegalArgumentException("Ukjent MalParameter: $value")
    }
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
    fun valueOf(name: String): Mal = when (name.substringBefore(":")) {
        VurdertSomAktuell.name -> VurdertSomAktuell
        PassendeStilling.name -> PassendeStilling
        PassendeJobbarrangement.name -> PassendeJobbarrangement
        KandidatInvitertTreff.name -> KandidatInvitertTreff
        KandidatInvitertTreffEndret.name -> KandidatInvitertTreffEndret
        else -> throw IllegalArgumentException("Ukjent Mal: $name")
    }
    
    /** Parser mal-streng som kan inneholde parametere, f.eks. "KANDIDAT_INVITERT_TREFF_ENDRET:TITTEL,STED" */
    fun parseValueOf(malStreng: String): Pair<Mal, List<MalParameter>?> {
        val parts = malStreng.split(":", limit = 2)
        val mal = valueOf(parts[0])
        val malParametere = if (parts.size > 1 && parts[1].isNotEmpty()) {
            parts[1].split(",").map { MalParameter.valueOf(it) }
        } else {
            null
        }
        return Pair(mal, malParametere)
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
        "Du er invitert til et treff med arbeidsgivere. Du kan melde deg på inne på minside hos Nav."

    override fun smsTekst() =
        "Hei! Du er invitert til et treff med arbeidsgivere. Logg inn på Nav for å melde deg på. Vennlig hilsen Nav"

    override fun epostTittel() =
        "Du er invitert til et treff"

    override fun epostHtmlBody() =
        Maler.epostHtmlBodyTemplate(
            """
                    Du er invitert til et treff med arbeidsgivere. Logg inn på Nav for å melde deg på.
                """.trimIndent()
        )
}

data object KandidatInvitertTreffEndret : RekrutteringstreffMal {
    override val name = "KANDIDAT_INVITERT_TREFF_ENDRET"
    
    const val PLACEHOLDER = "{{ENDRINGER}}"

    override fun minsideTekst() =
        "Det har skjedd endringer i $PLACEHOLDER knyttet til et treff med arbeidsgivere som du er invitert til. Se mer her."

    override fun smsTekst() =
        "Hei! Det har skjedd endringer i $PLACEHOLDER på et treff med arbeidsgivere du er invitert til. Logg inn på Nav for mer informasjon. Vennlig hilsen Nav"

    override fun epostTittel() =
        "Endringer på treff du er invitert til"

    override fun epostHtmlBody() =
        Maler.epostHtmlBodyTemplate(
            """
                    Det har skjedd endringer i $PLACEHOLDER på et treff med arbeidsgivere du er invitert til. Logg inn på Nav for mer informasjon.
                """.trimIndent()
        )
    
    fun minsideTekst(malParametere: List<MalParameter>) =
        minsideTekst().replace(PLACEHOLDER, formaterParametere(malParametere))
    
    fun smsTekst(malParametere: List<MalParameter>) =
        smsTekst().replace(PLACEHOLDER, formaterParametere(malParametere))
    
    fun epostHtmlBody(malParametere: List<MalParameter>) =
        epostHtmlBody().replace(PLACEHOLDER, formaterParametere(malParametere))
    
    private fun formaterParametere(parametere: List<MalParameter>): String {
        if (parametere.isEmpty()) {
            return "ukjente felter" // Fallback som ikke skal skje, men unngår exception
        }
        val tekster = parametere.map { it.displayTekst }
        return when (tekster.size) {
            1 -> tekster.first()
            else -> tekster.dropLast(1).joinToString(", ") + " og " + tekster.last()
        }
    }
}