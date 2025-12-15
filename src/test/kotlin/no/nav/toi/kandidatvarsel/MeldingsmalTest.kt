package no.nav.toi.kandidatvarsel

import no.nav.toi.kandidatvarsel.minside.Maler.epostHtmlBodyTemplate
import no.nav.toi.kandidatvarsel.minside.sendBestilling
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
            // KandidatInvitertTreff - verifiser hele tekster
            assertEquals(
                "Hei! Du er invitert til et treff der du kan møte arbeidsgivere. Logg inn på Nav for å melde deg på. Vennlig hilsen Nav",
                meldingsmal.kandidatInvitertTreff.smsTekst
            )
            assertEquals("Invitasjon til å treffe arbeidsgivere", meldingsmal.kandidatInvitertTreff.epostTittel)
            assertEquals(
                """
                <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Hei! Du er invitert til et treff der du kan møte arbeidsgivere. Logg inn på Nav for å melde deg på.</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
                """.trimIndent(),
                meldingsmal.kandidatInvitertTreff.epostHtmlBody
            )
            
            // KandidatInvitertTreffEndret - verifiser hele tekster med placeholder
            assertEquals("{{ENDRINGER}}", meldingsmal.kandidatInvitertTreffEndret.placeholder)
            assertEquals(
                "Det er endringer i et treff du er invitert til: {{ENDRINGER}}. Logg inn på Nav for å se detaljer.",
                meldingsmal.kandidatInvitertTreffEndret.smsTekst
            )
            assertEquals("Endringer på treff du er invitert til", meldingsmal.kandidatInvitertTreffEndret.epostTittel)
            assertEquals(
                """
                <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Det har skjedd endringer på et treff med arbeidsgivere som du er invitert til:</p><p>{{ENDRINGER}}</p><p>Logg inn på Nav for mer informasjon.</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
                """.trimIndent(),
                meldingsmal.kandidatInvitertTreffEndret.epostHtmlBody
            )
            
            // Sjekk at alle endringsFelt er med
            assertEquals(5, meldingsmal.kandidatInvitertTreffEndret.endringsFelt.size)
            val feltKoder = meldingsmal.kandidatInvitertTreffEndret.endringsFelt.map { it.kode }
            assertTrue(feltKoder.contains("NAVN"))
            assertTrue(feltKoder.contains("TIDSPUNKT"))
            assertTrue(feltKoder.contains("SVARFRIST"))
            assertTrue(feltKoder.contains("STED"))
            assertTrue(feltKoder.contains("INTRODUKSJON"))
        }
    }
    
    @Test
    fun `KandidatInvitertTreffEndret formaterer endringsTekster korrekt`() {
        val mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret
        
        // Én endring - verifiser hele teksten
        assertEquals(
            "Det er endringer i et treff du er invitert til: navn. Logg inn på Nav for å se detaljer.",
            mal.smsTekst(listOf("navn"))
        )
        
        // To endringer - bruk "og"
        assertEquals(
            "Det er endringer i et treff du er invitert til: tidspunkt og sted. Logg inn på Nav for å se detaljer.",
            mal.smsTekst(listOf("tidspunkt", "sted"))
        )
        
        // Tre endringer - bruk komma og "og"
        assertEquals(
            "Det er endringer i et treff du er invitert til: navn, tidspunkt og sted. Logg inn på Nav for å se detaljer.",
            mal.smsTekst(listOf("navn", "tidspunkt", "sted"))
        )
        
        // Fire endringer
        assertEquals(
            "Det er endringer i et treff du er invitert til: navn, tidspunkt, svarfrist og sted. Logg inn på Nav for å se detaljer.",
            mal.smsTekst(listOf("navn", "tidspunkt", "svarfrist", "sted"))
        )
        
        // Alle fem endringer - verifiser at den er under 160 tegn
        val smsMedAlleEndringer = mal.smsTekst(listOf("navn", "tidspunkt", "svarfrist", "sted", "introduksjon"))
        assertEquals(
            "Det er endringer i et treff du er invitert til: navn, tidspunkt, svarfrist, sted og introduksjon. Logg inn på Nav for å se detaljer.",
            smsMedAlleEndringer
        )
        assertTrue(smsMedAlleEndringer.length <= 160, "SMS med alle 5 endringer skal være maks 160 tegn, men var ${smsMedAlleEndringer.length}")
    }
    
    @Test
    fun `KandidatInvitertTreff SMS er under 160 tegn`() {
        val mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff
        val smsTekst = mal.smsTekst()
        assertTrue(smsTekst.length <= 160, "SMS skal være maks 160 tegn, men var ${smsTekst.length}")
    }
    
    @Test
    fun `KandidatInvitertTreffEndret SMS med alle endringer er under 160 tegn`() {
        val mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret
        val alleEndringer = listOf("navn", "tidspunkt", "svarfrist", "sted", "introduksjon")
        val smsTekst = mal.smsTekst(alleEndringer)
        
        assertTrue(smsTekst.length <= 160, 
            "SMS med alle endringer skal være maks 160 tegn, men var ${smsTekst.length}: '$smsTekst'")
    }
    
    @Test
    fun `SMS over 160 tegn skal fanges opp`() {
        // Denne testen verifiserer at vi vil oppdage hvis noen legger til lengre tekst
        val maksSmsTegn = 160
        
        val kandidatInvitertTreff = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff
        assertTrue(kandidatInvitertTreff.smsTekst().length <= maksSmsTegn,
            "KandidatInvitertTreff SMS skal være maks $maksSmsTegn tegn")
        
        val kandidatInvitertTreffEndret = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret
        val alleEndringer = listOf("navn", "tidspunkt", "svarfrist", "sted", "introduksjon")
        assertTrue(kandidatInvitertTreffEndret.smsTekst(alleEndringer).length <= maksSmsTegn,
            "KandidatInvitertTreffEndret SMS med alle endringer skal være maks $maksSmsTegn tegn")
    }
    
    @Test
    fun `KandidatInvitertTreffEndret minsideTekst verifiseres eksakt`() {
        val mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret
        
        // Verifiser minsideTekst() med placeholder eksakt
        assertEquals(
            "Det har skjedd endringer i {{ENDRINGER}} knyttet til et treff med arbeidsgivere som du er invitert til.",
            mal.minsideTekst()
        )
        
        // Verifiser at minsideTekst med endringsTekster erstatter placeholder eksakt
        assertEquals(
            "Det har skjedd endringer i navn og tidspunkt knyttet til et treff med arbeidsgivere som du er invitert til.",
            mal.minsideTekst(listOf("navn", "tidspunkt"))
        )
    }
    
    @Test
    fun `KandidatInvitertTreffEndret epostHtmlBody verifiseres eksakt`() {
        val mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret
        
        // Verifiser epostHtmlBody() med placeholder eksakt
        assertEquals(
            """
            <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Det har skjedd endringer på et treff med arbeidsgivere som du er invitert til:</p><p>{{ENDRINGER}}</p><p>Logg inn på Nav for mer informasjon.</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
            """.trimIndent(),
            mal.epostHtmlBody()
        )
        
        // Verifiser at epostHtmlBody med endringsTekster erstatter placeholder eksakt
        assertEquals(
            """
            <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Det har skjedd endringer på et treff med arbeidsgivere som du er invitert til:</p><p>sted</p><p>Logg inn på Nav for mer informasjon.</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
            """.trimIndent(),
            mal.epostHtmlBody(listOf("sted"))
        )
    }
    
    @Test
    fun `database round-trip for varsel med flettedata`() {
        // Opprett varsel med flettedata (displayTekster for endringene)
        val originalFlettedata = listOf("navn", "tidspunkt", "sted")
        val rekrutteringstreffId = "test-roundtrip-${System.currentTimeMillis()}"
        
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = "12345678901",
                avsenderNavident = "Z123456",
                flettedata = originalFlettedata
            ).insert(tx)
        }
        
        // Hent varselet tilbake fra databasen
        val hentetVarsel = app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId).first()
        }
        
        // Verifiser at flettedata er bevart
        assertEquals(no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret, hentetVarsel.mal)
        assertEquals(originalFlettedata, hentetVarsel.flettedata)
    }
    
    @Test
    fun `sendBestilling kaster IllegalStateException for KandidatInvitertTreffEndret uten flettedata`() {
        val varselUtenFlettedata = no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
            mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret,
            avsenderReferanseId = "test-treff-id",
            mottakerFnr = "12345678901",
            avsenderNavident = "Z123456",
            flettedata = emptyList()
        )
        
        val mockProducer = org.apache.kafka.clients.producer.MockProducer<String, String>()
        
        val exception = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            mockProducer.sendBestilling(
                varselUtenFlettedata,
                no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret
            )
        }
        
        assertTrue(exception.message?.contains("KandidatInvitertTreffEndret krever at data er satt") == true)
    }
    
    @Test
    fun `sendBestilling kaster IllegalStateException for KandidatInvitertTreffEndret med null flettedata`() {
        val varselMedNullFlettedata = no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
            mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret,
            avsenderReferanseId = "test-treff-id",
            mottakerFnr = "12345678901",
            avsenderNavident = "Z123456",
            flettedata = null
        )
        
        val mockProducer = org.apache.kafka.clients.producer.MockProducer<String, String>()
        
        val exception = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            mockProducer.sendBestilling(
                varselMedNullFlettedata,
                no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret
            )
        }
        
        assertTrue(exception.message?.contains("KandidatInvitertTreffEndret krever at data er satt") == true)
    }


}