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
    fun `skal opprette varsel når rekrutteringstreffSvarOgStatus med svar=true og treffstatus=avlyst mottas`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"

        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffSvarOgStatus",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "hendelseId": "$hendelseId",
                "svar": true,
                "treffstatus": "avlyst",
                "endretAv": "12345678901",
                "endretAvPersonbruker": false
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
    fun `skal ikke opprette varsel når svar er false selv om treffstatus er avlyst`() {
        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffSvarOgStatus",
                "rekrutteringstreffId": "12345678-1234-1234-1234-123456789012",
                "fnr": "12345678901",
                "hendelseId": "87654321-4321-4321-4321-210987654321",
                "svar": false,
                "treffstatus": "avlyst",
                "endretAv": "12345678901",
                "endretAvPersonbruker": false
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, "12345678-1234-1234-1234-123456789012")
        }

        assertEquals(0, varsler.size)
    }

    @Test
    fun `skal ikke opprette varsel når treffstatus ikke er avlyst`() {
        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffSvarOgStatus",
                "rekrutteringstreffId": "12345678-1234-1234-1234-123456789012",
                "fnr": "12345678901",
                "hendelseId": "87654321-4321-4321-4321-210987654321",
                "svar": true,
                "treffstatus": "fullført",
                "endretAv": "12345678901",
                "endretAvPersonbruker": false
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, "12345678-1234-1234-1234-123456789012")
        }

        assertEquals(0, varsler.size)
    }

    @Test
    fun `skal ignorere melding som mangler hendelseId`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"

        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffSvarOgStatus",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "12345678901",
                "svar": true,
                "treffstatus": "avlyst"
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }

        assertEquals(0, varsler.size)
    }
}
