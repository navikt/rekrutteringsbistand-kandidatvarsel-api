package no.nav.toi.kandidatvarsel

import no.nav.toi.kandidatvarsel.minside.Mal.Companion.epostHtmlBodyTemplate
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MeldingsmalTest {
    private val app = LocalApp()
    @BeforeEach
    fun beforeEach() = app.prepare()
    @AfterAll
    fun afterAll() = app.close()
    private val veileder1 = app.userToken(navIdent = "Z1")


    @Test
    fun meldingsmal() {

        app.getMeldingsmal(veileder1).also { meldingsmal ->
            assertEquals(meldingsmal.vurdertSomAktuellSmsTekst, "Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på NAV for å se stillingen. Vennlig hilsen NAV")
            assertEquals(meldingsmal.vurdertSomAktuellEpostTittel, "Stilling som kan passe for deg?")
            assertEquals(meldingsmal.vurdertSomAktuellEpostHtmlBody,
                epostHtmlBodyTemplate("""
                    Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på NAV for å se stillingen.
                """.trimIndent())
            )
            assertEquals(meldingsmal.passendeStillingSmsTekst, "Hei! Vi har funnet en stilling som kan passe deg. Logg inn på NAV for å se stillingen. Vennlig hilsen NAV")
            assertEquals(meldingsmal.passendeStillingEpostTittel, "Stilling som kan passe for deg?")
            assertEquals(meldingsmal.passendeStillingEpostHtmlBody,
                epostHtmlBodyTemplate("""
                    Vi har funnet en stilling som kanskje kan passe for deg. Logg inn på NAV for å se stillingen.
                """.trimIndent())
            )
            assertEquals(meldingsmal.passendeJobbarrangementSmsTekst, "Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på NAV for å se arrangementet. Vennlig hilsen NAV")
            assertEquals(meldingsmal.passendeJobbarrangementEpostTittel, "Jobbarrangement")
            assertEquals(meldingsmal.passendeJobbarrangementEpostHtmlBody,
                epostHtmlBodyTemplate("""
                    Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på NAV for å se arrangementet.
                """.trimIndent())
            )
        }
    }
}