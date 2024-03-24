package no.nav.toi.kandidatvarsel.minside

sealed interface Mal {
    val name: String
    fun minsideTekst(stillingsTittel: String, arbeidsgiver: String): String
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
            override fun minsideTekst(stillingsTittel: String, arbeidsgiver: String) =
                "Vi har vurdert at kompetansen din kan passe til stillingen «$stillingsTittel» hos «$arbeidsgiver»"
            override fun smsTekst() =
                "Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på NAV for å se stillingen. Vennlig hilsen NAV"
            override fun epostTittel() =
//                "Stilling som kompetansen din kan passe til" // TODO 42 tegn, maks 40
                "Stilling som du kan passe til"
            override fun epostHtmlBody() =
                "Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på NAV for å se stillingen. Vennlig hilsen NAV"
        }

        data object PassendeStilling: Mal {
            override val name = "PASSENDE_STILLING"
            override fun minsideTekst(stillingsTittel: String, arbeidsgiver: String) =
                "Vi har funnet stillingen «$stillingsTittel» hos «$arbeidsgiver» som kan passe deg. Interessert? Søk via lenka i annonsen."
            override fun smsTekst() =
                "Hei! Vi har funnet en stilling som kan passe deg. Logg inn på NAV for å se stillingen. Vennlig hilsen NAV"
            override fun epostTittel() =
                "Stilling som kan passe for deg?"
            override fun epostHtmlBody() =
                "Hei! Vi har funnet en stilling som kanskje kan passe for deg. Logg inn på NAV for å se stillingen. Vennlig hilsen NAV"
        }

        data object PassendeJobbarrangement: Mal {
            override val name = "PASSENDE_JOBBARRANGEMENT"
            override fun minsideTekst(stillingsTittel: String, arbeidsgiver: String) =
                "Vi har et jobbarrangement som kanskje passer for deg"
            override fun smsTekst() =
                "Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på NAV for å se arrangementet. Vennlig hilsen NAV"
            override fun epostTittel() =
//                "Jobbarrangement som kanskje passer for deg" // TODO: 42 tegn, maks 40
                "Jobbarrangement som kanskje passer"
            override fun epostHtmlBody() =
                "Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på NAV for å se arrangementet. Vennlig hilsen NAV"
        }
    }
}