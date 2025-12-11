package no.nav.toi.kandidatvarsel.rapids.lyttere

import no.nav.toi.kandidatvarsel.util.TestRapid
import no.nav.toi.kandidatvarsel.DatabaseConfig
import no.nav.toi.kandidatvarsel.minside.*
import no.nav.toi.kandidatvarsel.transaction
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KandidatInvitertTreffEndretLytterTest {

    private val postgres = PostgreSQLContainer("postgres:15").apply { start() }
    private val dataSource = DatabaseConfig(
        hostname = postgres.host,
        port = postgres.firstMappedPort,
        database = postgres.databaseName,
        username = postgres.username,
        password = postgres.password
    ).createDataSource()

    private val testRapid = TestRapid()
    
    @BeforeAll
    fun setup() {
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
            
        KandidatInvitertTreffEndretLytter(testRapid, dataSource)
    }

    @BeforeEach
    fun reset() {
        testRapid.reset()
        dataSource.transaction { tx ->
            tx.sql("DELETE FROM minside_varsel").update()
        }
    }

    @AfterAll
    fun teardown() {
        dataSource.close()
        postgres.stop()
    }

    @Test
    fun `skal opprette varsel når kandidat invitert treff endret melding mottas`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"

        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffoppdatering",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "endretAv": "Z123456",
                "hendelseId": "$hendelseId",
                "endredeFelter": ["NAVN", "TIDSPUNKT"]
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }

        assertEquals(1, varsler.size)
        assertEquals(KandidatInvitertTreffEndret.name, varsler[0].mal.name)
        assertEquals(rekrutteringstreffId, varsler[0].avsenderReferanseId)
        assertEquals("Z123456", varsler[0].avsenderNavIdent)
        assertEquals(fnr, varsler[0].mottakerFnr)
        assertEquals(hendelseId, varsler[0].varselId)
        assertEquals(listOf("navn", "tidspunkt"), varsler[0].flettedata)  // Konvertert til displayTekst
    }

    @Test
    fun `skal bruke SYSTEM som avsender når endretAv mangler`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"

        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffoppdatering",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "hendelseId": "$hendelseId",
                "endredeFelter": ["STED"]
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }

        assertEquals(1, varsler.size)
        assertEquals("SYSTEM", varsler[0].avsenderNavIdent)
    }
    
    @Test
    fun `skal ikke opprette varsel når rekrutteringstreffId mangler`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        
        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffoppdatering",
                "fnr": "12345678901",
                "endretAv": "Z123456",
                "hendelseId": "87654321-4321-4321-4321-210987654321",
                "endredeFelter": ["NAVN"]
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }
        
        assertEquals(0, varsler.size)
    }
    
    @Test
    fun `skal ikke opprette varsel når fnr mangler`() {
        val varselId = "12345678-1234-1234-1234-123456789012"
        
        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffoppdatering",
                "varselId": "$varselId",
                "avsenderNavident": "Z123456",
                "endredeFelter": ["INTRODUKSJON"]
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, varselId)
        }
        
        assertEquals(0, varsler.size)
    }
    
    @Test
    fun `skal ikke opprette varsel når endredeFelter mangler`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"
        
        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffoppdatering",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "endretAv": "Z123456",
                "hendelseId": "$hendelseId"
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }
        
        assertEquals(0, varsler.size)
    }
    
    @Test
    fun `skal ikke opprette varsel når endredeFelter er tom liste`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"
        
        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffoppdatering",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "endretAv": "Z123456",
                "hendelseId": "$hendelseId",
                "endredeFelter": []
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }
        
        assertEquals(0, varsler.size)
    }
    
    @Test
    fun `skal ikke opprette varsel når endredeFelter kun inneholder ugyldige verdier`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"
        
        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffoppdatering",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "endretAv": "Z123456",
                "hendelseId": "$hendelseId",
                "endredeFelter": ["UGYLDIG", "FINNES_IKKE"]
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }
        
        assertEquals(0, varsler.size)
    }
    
    @Test
    fun `skal filtrere ut ugyldige endredeFelter-verdier`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"

        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffoppdatering",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "endretAv": "Z123456",
                "hendelseId": "$hendelseId",
                "endredeFelter": ["NAVN", "UGYLDIG", "TIDSPUNKT", "FINNES_IKKE"]
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }

        assertEquals(1, varsler.size)
        assertEquals(listOf("navn", "tidspunkt"), varsler[0].flettedata)  // Kun gyldige konverteres til displayTekst
    }
    
    @Test
    fun `skal konvertere alle gyldige endredeFelter-verdier til displayTekst`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"

        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffoppdatering",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "endretAv": "Z123456",
                "hendelseId": "$hendelseId",
                "endredeFelter": ["NAVN", "TIDSPUNKT", "SVARFRIST", "STED", "INTRODUKSJON"]
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }

        assertEquals(1, varsler.size)
        assertEquals(
            listOf("navn", "tidspunkt", "svarfrist", "sted", "introduksjon"),  // Konvertert til displayTekst
            varsler[0].flettedata
        )
    }
}
