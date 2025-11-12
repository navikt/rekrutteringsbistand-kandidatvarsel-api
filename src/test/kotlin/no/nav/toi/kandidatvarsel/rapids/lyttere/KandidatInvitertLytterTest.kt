package no.nav.toi.kandidatvarsel.rapids.lyttere

import no.nav.toi.kandidatvarsel.util.TestRapid
import no.nav.toi.kandidatvarsel.DatabaseConfig
import no.nav.toi.kandidatvarsel.minside.Mal
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel
import no.nav.toi.kandidatvarsel.transaction
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KandidatInvitertLytterTest {

    private val postgres = PostgreSQLContainer("postgres:14").apply { start() }
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
            
        KandidatInvitertLytter(testRapid, dataSource)
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
    fun `skal opprette varsel når kandidat invitert melding mottas`() {
        val varselId = "12345678-1234-1234-1234-123456789012"
        val fnr1 = "12345678901"
        val fnr2 = "12345678902"

        testRapid.sendTestMessage("""
            {
                "@event_name": "kandidat.invitert",
                "varselId": "$varselId",
                "fnr": ["$fnr1", "$fnr2"],
                "avsenderNavident": "Z123456"
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, varselId)
        }

        assertEquals(2, varsler.size)
        
        varsler.forEach { varsel ->
            assertEquals(Mal.Companion.KandidatInvitertTreff.name, varsel.mal.name)
            assertEquals(varselId, varsel.stillingId)
            assertEquals("Z123456", varsel.avsenderNavIdent)
            assertTrue(listOf(fnr1, fnr2).contains(varsel.mottakerFnr))
        }
    }

    @Test
    fun `skal bruke SYSTEM som default avsender når avsenderNavident mangler`() {
        val varselId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"

        testRapid.sendTestMessage("""
            {
                "@event_name": "kandidat.invitert",
                "varselId": "$varselId",
                "fnr": ["$fnr"]
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, varselId)
        }

        assertEquals(1, varsler.size)
        assertEquals("SYSTEM", varsler[0].avsenderNavIdent)
    }

    @Test
    fun `skal håndtere enkelt fnr som ikke er array`() {
        val varselId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"

        testRapid.sendTestMessage("""
            {
                "@event_name": "kandidat.invitert",
                "varselId": "$varselId",
                "fnr": "$fnr"
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, varselId)
        }

        assertEquals(1, varsler.size)
        assertEquals(fnr, varsler[0].mottakerFnr)
    }
}
