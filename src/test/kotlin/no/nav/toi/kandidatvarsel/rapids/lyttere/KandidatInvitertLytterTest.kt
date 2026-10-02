package no.nav.toi.kandidatvarsel.rapids.lyttere

import no.nav.toi.kandidatvarsel.util.TestRapid
import no.nav.toi.kandidatvarsel.DatabaseConfig
import no.nav.toi.kandidatvarsel.minside.*
import no.nav.toi.kandidatvarsel.transaction
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.testcontainers.postgresql.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KandidatInvitertLytterTest {

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
            
        KandidatInvitertLytter(testRapid, dataSource, "rekrutteringstreffinvitasjon", KandidatInvitertTreff)
        KandidatInvitertLytter(testRapid, dataSource, "workopinvitasjon", KandidatInvitertWorkOp)
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
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"

        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffinvitasjon",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "opprettetAv": "Z123456",
                "hendelseId": "$hendelseId"
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }

        assertEquals(1, varsler.size)
        assertEquals(KandidatInvitertTreff.name, varsler[0].mal.name)
        assertEquals(rekrutteringstreffId, varsler[0].avsenderReferanseId)
        assertEquals("Z123456", varsler[0].avsenderNavIdent)
        assertEquals(fnr, varsler[0].mottakerFnr)
        assertEquals(hendelseId, varsler[0].varselId)
    }

    @Test
    fun `skal opprette workop-varsel når workopinvitasjon melding mottas`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        val fnr = "12345678901"
        val hendelseId = "87654321-4321-4321-4321-210987654321"

        testRapid.sendTestMessage("""
            {
                "@event_name": "workopinvitasjon",
                "rekrutteringstreffId": "$rekrutteringstreffId",
                "fnr": "$fnr",
                "opprettetAv": "Z123456",
                "hendelseId": "$hendelseId"
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId)
        }

        assertEquals(1, varsler.size)
        assertEquals(KandidatInvitertWorkOp.name, varsler[0].mal.name)
        assertEquals(rekrutteringstreffId, varsler[0].avsenderReferanseId)
        assertEquals("Z123456", varsler[0].avsenderNavIdent)
        assertEquals(fnr, varsler[0].mottakerFnr)
        assertEquals(hendelseId, varsler[0].varselId)
    }
    
    @ParameterizedTest
    @CsvSource(
        "rekrutteringstreffinvitasjon, KANDIDAT_INVITERT_TREFF",
        "workopinvitasjon, KANDIDAT_INVITERT_WORKOP"
    )
    fun `skal beholde ett uendret varsel når samme invitasjon mottas på nytt`(eventName: String, mal: String) {
        val referanseId = "12345678-1234-1234-1234-123456789012"
        val hendelseId = "87654321-4321-4321-4321-210987654321"
        val melding = invitasjonMelding(eventName, referanseId, hendelseId)

        testRapid.sendTestMessage(melding)
        val før = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, referanseId)
        }
        assertEquals(1, før.size)
        assertEquals(mal, før.single().mal.name)
        assertEquals(hendelseId, før.single().varselId)
        assertEquals(referanseId, før.single().avsenderReferanseId)
        assertEquals("12345678901", før.single().mottakerFnr)
        assertEquals("Z123456", før.single().avsenderNavIdent)

        testRapid.sendTestMessage(melding)
        val etter = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, referanseId)
        }
        assertEquals(1, etter.size)
        assertEquals(før.single(), etter.single())
    }

    @ParameterizedTest
    @CsvSource(
        "rekrutteringstreffinvitasjon, KANDIDAT_INVITERT_TREFF",
        "workopinvitasjon, KANDIDAT_INVITERT_WORKOP"
    )
    fun `skal opprette separate varsler for invitasjoner med ulike hendelseId`(eventName: String, mal: String) {
        val referanseId = "12345678-1234-1234-1234-123456789012"
        val hendelseIder = listOf(
            "87654321-4321-4321-4321-210987654321",
            "87654321-4321-4321-4321-210987654322"
        )

        hendelseIder.forEach { testRapid.sendTestMessage(invitasjonMelding(eventName, referanseId, it)) }
        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, referanseId)
        }
        assertEquals(2, varsler.size)
        assertEquals(hendelseIder.toSet(), varsler.map { it.varselId }.toSet())
        varsler.forEach {
            assertEquals(mal, it.mal.name)
            assertEquals(referanseId, it.avsenderReferanseId)
            assertEquals("12345678901", it.mottakerFnr)
            assertEquals("Z123456", it.avsenderNavIdent)
        }
    }

    private fun invitasjonMelding(eventName: String, referanseId: String, hendelseId: String) = """
        {
            "@event_name": "$eventName",
            "rekrutteringstreffId": "$referanseId",
            "fnr": "12345678901",
            "opprettetAv": "Z123456",
            "hendelseId": "$hendelseId"
        }
    """.trimIndent()

    @Test
    fun `skal ikke opprette varsel når rekrutteringstreffId mangler`() {
        val rekrutteringstreffId = "12345678-1234-1234-1234-123456789012"
        
        testRapid.sendTestMessage("""
            {
                "@event_name": "rekrutteringstreffinvitasjon",
                "fnr": "12345678901",
                "opprettetAv": "Z123456",
                "hendelseId": "87654321-4321-4321-4321-210987654321"
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
                "@event_name": "rekrutteringstreffinvitasjon",
                "varselId": "$varselId",
                "avsenderNavident": "Z123456"
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, varselId)
        }
        
        assertEquals(0, varsler.size)
    }
    
    @Test
    fun `skal ikke opprette varsel når avsenderNavident mangler`() {
        val varselId = "12345678-1234-1234-1234-123456789012"
        
        testRapid.sendTestMessage("""
            {
                "@event_name": "kandidatInvitert",
                "varselId": "$varselId",
                "fnr": "12345678901"
            }
        """.trimIndent())

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForRekrutteringstreff(tx, varselId)
        }
        
        assertEquals(0, varsler.size)
    }
}
