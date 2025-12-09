package no.nav.toi.kandidatvarsel

import no.nav.toi.kandidatvarsel.minside.Maler.epostHtmlBodyTemplate
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
            assertEquals(meldingsmal.vurdertSomAktuell.smsTekst, "Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav")
            assertEquals(meldingsmal.vurdertSomAktuell.epostTittel, "Stilling som kan passe for deg?")
            assertEquals(meldingsmal.vurdertSomAktuell.epostHtmlBody,
                epostHtmlBodyTemplate("""
                    Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen.
                """.trimIndent())
            )
            assertEquals(meldingsmal.passendeStilling.smsTekst, "Hei! Vi har funnet en stilling som kan passe deg. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav")
            assertEquals(meldingsmal.passendeStilling.epostTittel, "Stilling som kan passe for deg?")
            assertEquals(meldingsmal.passendeStilling.epostHtmlBody,
                epostHtmlBodyTemplate("""
                    Vi har funnet en stilling som kanskje kan passe for deg. Logg inn på Nav for å se stillingen.
                """.trimIndent())
            )
            assertEquals(meldingsmal.passendeJobbarrangement.smsTekst, "Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet. Vennlig hilsen Nav")
            assertEquals(meldingsmal.passendeJobbarrangement.epostTittel, "Jobbarrangement")
            assertEquals(meldingsmal.passendeJobbarrangement.epostHtmlBody,
                epostHtmlBodyTemplate("""
                    Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet.
                """.trimIndent())
            )
        }
    }

    @Test
    fun stillingMeldingsmal() {
        app.getStillingMeldingsmal(veileder1).also { meldingsmal ->
            assertEquals(meldingsmal.vurdertSomAktuell.smsTekst, "Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav")
            assertEquals(meldingsmal.vurdertSomAktuell.epostTittel, "Stilling som kan passe for deg?")
            assertEquals(meldingsmal.vurdertSomAktuell.epostHtmlBody,
                epostHtmlBodyTemplate("""
                    Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen.
                """.trimIndent())
            )
            assertEquals(meldingsmal.passendeStilling.smsTekst, "Hei! Vi har funnet en stilling som kan passe deg. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav")
            assertEquals(meldingsmal.passendeStilling.epostTittel, "Stilling som kan passe for deg?")
            assertEquals(meldingsmal.passendeStilling.epostHtmlBody,
                epostHtmlBodyTemplate("""
                    Vi har funnet en stilling som kanskje kan passe for deg. Logg inn på Nav for å se stillingen.
                """.trimIndent())
            )
            assertEquals(meldingsmal.passendeJobbarrangement.smsTekst, "Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet. Vennlig hilsen Nav")
            assertEquals(meldingsmal.passendeJobbarrangement.epostTittel, "Jobbarrangement")
            assertEquals(meldingsmal.passendeJobbarrangement.epostHtmlBody,
                epostHtmlBodyTemplate("""
                    Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet.
                """.trimIndent())
            )
        }
    }

    @Test
    fun rekrutteringstreffMeldingsmal() {
        app.getRekrutteringstreffMeldingsmal(veileder1).also { meldingsmal ->
            assertEquals(meldingsmal.kandidatInvitertTreff.smsTekst, "Hei! Du er invitert til et treff der du kan møte arbeidsgivere. Logg inn på Nav for å melde deg på. Vennlig hilsen Nav")
            assertEquals(meldingsmal.kandidatInvitertTreff.epostTittel, "Invitasjon til å treffe arbeidsgivere")
            assertEquals(meldingsmal.kandidatInvitertTreff.epostHtmlBody,
                """
                <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Hei! Du er invitert til et treff der du kan møte arbeidsgivere. Logg inn på Nav for å melde deg på.</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
                """.trimIndent()
            )
            // Sjekk at KANDIDAT_INVITERT_TREFF_ENDRET har placeholder
            assertEquals("{{ENDRINGER}}", meldingsmal.kandidatInvitertTreffEndret.placeholder)
            assertTrue(meldingsmal.kandidatInvitertTreffEndret.smsTekst.contains("{{ENDRINGER}}"))
            assertTrue(meldingsmal.kandidatInvitertTreffEndret.epostHtmlBody.contains("{{ENDRINGER}}"))
            assertEquals(meldingsmal.kandidatInvitertTreffEndret.epostTittel, "Endringer på treff du er invitert til")
            
            // Sjekk at alle malParametere er med
            assertEquals(5, meldingsmal.kandidatInvitertTreffEndret.malParametere.size)
            val parametere = meldingsmal.kandidatInvitertTreffEndret.malParametere.map { it.kode }
            assertTrue(parametere.contains("NAVN"))
            assertTrue(parametere.contains("TIDSPUNKT"))
            assertTrue(parametere.contains("SVARFRIST"))
            assertTrue(parametere.contains("STED"))
            assertTrue(parametere.contains("INTRODUKSJON"))
        }
    }
    
    @Test
    fun `KandidatInvitertTreffEndret formaterer malParametere korrekt`() {
        val mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret
        
        // Én parameter
        assertTrue(mal.smsTekst(listOf(no.nav.toi.kandidatvarsel.minside.MalParameter.NAVN))
            .contains("navn"))
        
        // To parametere - bruk "og"
        assertTrue(mal.smsTekst(listOf(
            no.nav.toi.kandidatvarsel.minside.MalParameter.TIDSPUNKT,
            no.nav.toi.kandidatvarsel.minside.MalParameter.STED
        )).contains("tidspunkt og sted"))
        
        // Tre parametere - bruk komma og "og"
        assertTrue(mal.smsTekst(listOf(
            no.nav.toi.kandidatvarsel.minside.MalParameter.NAVN,
            no.nav.toi.kandidatvarsel.minside.MalParameter.TIDSPUNKT,
            no.nav.toi.kandidatvarsel.minside.MalParameter.STED
        )).contains("navn, tidspunkt og sted"))
    }
    


}