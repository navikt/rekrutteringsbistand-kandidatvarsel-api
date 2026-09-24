package no.nav.toi.kandidatvarsel.rapids.lyttere

import no.nav.toi.kandidatvarsel.DatabaseConfig
import no.nav.toi.kandidatvarsel.minside.DelCv
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel
import no.nav.toi.kandidatvarsel.transaction
import no.nav.toi.kandidatvarsel.util.TestRapid
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KandidatDelCvLytterTest {
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

        KandidatDelCvLytter(testRapid, dataSource)
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
    fun `skal opprette varsel når samtykke er forespurt`() {
        val fnr = "12345678901"
        val stillingId = UUID.randomUUID()
        val stillingsTittel = "Senior Utvikler"
        val forespurtAvIdent = "Z123456"

        testRapid.sendTestMessage(samtykkeForespurtMelding(
            fnr = fnr,
            stillingId = stillingId,
            stillingsTittel = stillingsTittel,
            forespurtAvIdent = forespurtAvIdent
        ))

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, stillingId.toString())
        }

        assertEquals(1, varsler.size)
        assertEquals(DelCv.name, varsler[0].mal.name)
        assertEquals(stillingId.toString(), varsler[0].avsenderReferanseId)
        assertEquals(forespurtAvIdent, varsler[0].avsenderNavIdent)
        assertEquals(fnr, varsler[0].mottakerFnr)
    }

    @Test
    fun `skal ikke opprette nytt varsel når meldingen republiseres med aktivitetskortuuid`() {
        val stillingId = UUID.randomUUID()
        val melding = samtykkeForespurtMelding(
            fnr = "12345678901",
            stillingId = stillingId,
            stillingsTittel = "Senior Utvikler",
            forespurtAvIdent = "Z123456",
        )

        testRapid.sendTestMessage(melding)
        testRapid.sendTestMessage(
            melding.replace(
                "\"fnr\":",
                "\"aktivitetskortuuid\": \"${UUID.randomUUID()}\",\n    \"fnr\":",
            )
        )

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, stillingId.toString())
        }

        assertEquals(1, varsler.size)
    }

    private fun samtykkeForespurtMelding(
        fnr: String,
        stillingId: UUID,
        stillingsTittel: String,
        forespurtAvIdent: String,
    ) = """
        {
            "@event_name": "samtykke-forespurt-om-deling-av-cv",
            "fnr": "$fnr",
            "stillingsId": "$stillingId",
            "stillingsTittel": "$stillingsTittel",
            "svarfrist": "${ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS)}",
            "forespurtAvIdent": "$forespurtAvIdent",
            "forespurtTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
        }
    """.trimIndent()
}