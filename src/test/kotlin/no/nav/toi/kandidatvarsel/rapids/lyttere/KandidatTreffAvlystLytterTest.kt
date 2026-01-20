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
class KandidatTreffAvlystLytterTest {

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
            
        KandidatTreffAvlystLytter(testRapid, dataSource)
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
    fun `skal opprette varsel når rekrutteringstreffavlysning melding mottas`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"

        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffavlysning",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "hendelseId": "$hendelseId",
                "tittel": "Jobbtreff hos bedrift AS"
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }

        assertEquals(1, varsler.size)
        assertEquals(KandidatInvitertTreffAvlyst.name, varsler[0].mal.name)
        assertEquals(rekrutteringstreffId, varsler[0].avsenderReferanseId)
        assertEquals("SYSTEM", varsler[0].avsenderNavIdent)
        assertEquals(fnr, varsler[0].mottakerFnr)
        assertEquals(hendelseId, varsler[0].varselId)
    }

    @Test
    fun `skal ikke opprette varsel uten påkrevde felt`() {
        // Mangler fnr
        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffavlysning",
                "rekrutteringstreffId": "12345678-1234-1234-1234-123456789012",
                "hendelseId": "87654321-4321-4321-4321-210987654321"
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, "12345678-1234-1234-1234-123456789012")
        }

        assertEquals(0, varsler.size)
    }
}
