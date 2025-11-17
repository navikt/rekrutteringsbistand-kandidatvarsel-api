package no.nav.toi.kandidatvarsel.minside

import no.nav.toi.kandidatvarsel.InvitertTreffKandidatEndret
import org.intellij.lang.annotations.Language

enum class VarselType {
    STILLING,
    REKRUTTERINGSTREFF
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
                    "rekrutteringstreff-minside-api.nav.no" 
                else 
                    "rekrutteringstreff-minside-api.ekstern.dev.nav.no"
                "https://$domain/api/rekrutteringstreff/$avsenderReferanseId"
            }
        }
    }
}

sealed interface StillingMal : Mal {
    override val varselType: VarselType get() = VarselType.STILLING
    fun minsideTekst(tittel: String, arbeidsgiver: String): String
}

sealed interface RekrutteringstreffMal : Mal {
    override val varselType: VarselType get() = VarselType.REKRUTTERINGSTREFF
    fun minsideTekst(): String
}

object Maler {
    fun valueOf(name: String): Mal = when (name) {
        VurdertSomAktuell.name -> VurdertSomAktuell
        PassendeStilling.name -> PassendeStilling
        PassendeJobbarrangement.name -> PassendeJobbarrangement
        KandidatInvitertTreff.name -> KandidatInvitertTreff
        InvitertKandidatTreffEndret.name -> InvitertKandidatTreffEndret
        else -> throw IllegalArgumentException("Ukjent Mal: $name")
    }

    fun malerForVarselType(varselType: VarselType): List<String> = when (varselType) {
        VarselType.STILLING -> listOf(
            VurdertSomAktuell.name,
            PassendeStilling.name,
            PassendeJobbarrangement.name
        )
        VarselType.REKRUTTERINGSTREFF -> listOf(
            KandidatInvitertTreff.name,
            InvitertKandidatTreffEndret.name
        )
    }
    
    fun epostHtmlBodyTemplate(@Language("HTML") tekst: String) = """
        <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Hei!</p><p>$tekst</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
    """.trimIndent()
}

data object VurdertSomAktuell: StillingMal {
            override val name = "VURDERT_SOM_AKTUELL"
            
            override fun minsideTekst(tittel: String, arbeidsgiver: String) =
                "Vi har vurdert at kompetansen din kan passe til stillingen «${tittel}» hos «${arbeidsgiver}». Se stillingen her."
            override fun smsTekst() =
                "Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav"
            override fun epostTittel() =
                "Stilling som kan passe for deg?"
            override fun epostHtmlBody() =
                Maler.epostHtmlBodyTemplate("""
                    Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen.
                """.trimIndent())
        }

        data object PassendeStilling: StillingMal {
            override val name = "PASSENDE_STILLING"
            
            override fun minsideTekst(tittel: String, arbeidsgiver: String) =
                "Vi har funnet stillingen «${tittel}» hos «${arbeidsgiver}» som kan passe deg. Interessert? Søk via lenka i annonsen."
            override fun smsTekst() =
                "Hei! Vi har funnet en stilling som kan passe deg. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav"
            override fun epostTittel() =
                "Stilling som kan passe for deg?"
            override fun epostHtmlBody() =
                Maler.epostHtmlBodyTemplate("""
                    Vi har funnet en stilling som kanskje kan passe for deg. Logg inn på Nav for å se stillingen.
                """.trimIndent())
        }

        data object PassendeJobbarrangement: StillingMal {
            override val name = "PASSENDE_JOBBARRANGEMENT"
            
            override fun minsideTekst(tittel: String, arbeidsgiver: String) =
                "Vi har et jobbarrangement som kanskje passer for deg"
            override fun smsTekst() =
                "Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet. Vennlig hilsen Nav"
            override fun epostTittel() =
                "Jobbarrangement"
            override fun epostHtmlBody() =
                Maler.epostHtmlBodyTemplate("""
                    Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet.
                """.trimIndent())
        }

        data object KandidatInvitertTreff: RekrutteringstreffMal {
            override val name = "KANDIDAT_INVITERT_TREFF"
            
            override fun minsideTekst() =
                "Du er invitert til et treff med arbeidsgivere. Du kan melde deg på inne på minside hos Nav."
            override fun smsTekst() =
                "Hei! Du er invitert til et treff med arbeidsgivere. Logg inn på Nav for å melde deg på. Vennlig hilsen Nav"
            override fun epostTittel() =
                "Du er invitert til et treff"
            override fun epostHtmlBody() =
                Maler.epostHtmlBodyTemplate("""
                    Du er invitert til et treff med arbeidsgivere. Logg inn på Nav for å melde deg på.
                """.trimIndent())
        }

        data object InvitertKandidatTreffEndret: RekrutteringstreffMal {
            override val name = "INVITERT_KANDIDAT_TREFF_ENDRET"
            
            override fun minsideTekst() =
                 "Det har skjedd endringer knyttet til et treff med arbeidsgivere som du er invitert til. Se mer her."
            override fun smsTekst() =
                "Hei! Det har skjedd endringer på et treff med arbeidsgivere du er invitert til. Logg inn på Nav for mer informasjon. Vennlig hilsen Nav"
            override fun epostTittel() =
                "Endringer på treff du er invitert til"
            override fun epostHtmlBody() =
                Maler.epostHtmlBodyTemplate("""
                    Det har skjedd endringer på et treff med arbeidsgivere du er invitert til. Logg inn på Nav for mer informasjon.
                """.trimIndent())
        }