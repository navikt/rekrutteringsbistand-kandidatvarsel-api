package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    private val objectMapper = jacksonObjectMapper()

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
        val hendelseId = UUID.randomUUID().toString()

        testRapid.sendTestMessage(samtykkeForespurtMelding(
            fnr = fnr,
            stillingId = stillingId,
            stillingsTittel = stillingsTittel,
            forespurtAvIdent = forespurtAvIdent,
            hendelseId = hendelseId,
        ))

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, stillingId.toString())
        }

        assertEquals(1, varsler.size)
        assertEquals(DelCv.name, varsler[0].mal.name)
        assertEquals(stillingId.toString(), varsler[0].avsenderReferanseId)
        assertEquals(forespurtAvIdent, varsler[0].avsenderNavIdent)
        assertEquals(fnr, varsler[0].mottakerFnr)
        assertEquals(hendelseId, varsler[0].varselId)
    }

    @Test
    fun `skal beholde ett uendret varsel når samme samtykkeforespørsel mottas på nytt`() {
        val stillingId = UUID.randomUUID()
        val hendelseId = UUID.randomUUID().toString()
        val melding = samtykkeForespurtMelding(
            fnr = "12345678901",
            stillingId = stillingId,
            stillingsTittel = "Senior Utvikler",
            forespurtAvIdent = "Z123456",
            hendelseId = hendelseId,
        )

        testRapid.sendTestMessage(melding)
        val før = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, stillingId.toString())
        }
        assertEquals(1, før.size)
        assertEquals(DelCv.name, før.single().mal.name)
        assertEquals(stillingId.toString(), før.single().avsenderReferanseId)
        assertEquals("12345678901", før.single().mottakerFnr)
        assertEquals("Z123456", før.single().avsenderNavIdent)

        testRapid.sendTestMessage(melding)
        val etter = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, stillingId.toString())
        }

        assertEquals(1, etter.size)
        assertEquals(før.single(), etter.single())
        assertEquals(hendelseId, etter.single().varselId)
    }

    @Test
    fun `skal opprette separate varsler for samtykkeforespørsler med ulike hendelseId`() {
        val stillingId = UUID.randomUUID()
        val hendelseIder = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val aktivitetskortuuid = UUID.randomUUID().toString()
        hendelseIder.forEach { hendelseId ->
            testRapid.sendTestMessage(samtykkeForespurtMelding(
                fnr = "12345678901",
                stillingId = stillingId,
                stillingsTittel = "Senior Utvikler",
                forespurtAvIdent = "Z123456",
                hendelseId = hendelseId,
                aktivitetskortuuid = aktivitetskortuuid,
            ))
        }
        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, stillingId.toString())
        }

        assertEquals(2, varsler.size)
        assertEquals(hendelseIder.toSet(), varsler.map { it.varselId }.toSet())
        varsler.forEach {
            assertEquals(DelCv.name, it.mal.name)
            assertEquals(stillingId.toString(), it.avsenderReferanseId)
            assertEquals("12345678901", it.mottakerFnr)
            assertEquals("Z123456", it.avsenderNavIdent)
        }
    }

    @Test
    fun `skal vente med varsling til melding er beriket med hendelseId og aktivitetskortuuid`() {
        val stillingId = UUID.randomUUID()
        val hendelseId = UUID.randomUUID().toString()
        val aktivitetskortuuid = UUID.randomUUID().toString()
        val opprinneligMelding = samtykkeForespurtMelding(
            fnr = "12345678901",
            stillingId = stillingId,
            stillingsTittel = "Senior Utvikler",
            forespurtAvIdent = "Z123456",
            hendelseId = null,
            aktivitetskortuuid = null,
        )

        testRapid.sendTestMessage(opprinneligMelding)
        val førBerikelse = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, stillingId.toString())
        }
        assertEquals(0, førBerikelse.size)

        val beriketMelding = samtykkeForespurtMelding(
            fnr = "12345678901",
            stillingId = stillingId,
            stillingsTittel = "Senior Utvikler",
            forespurtAvIdent = "Z123456",
            hendelseId = hendelseId,
            aktivitetskortuuid = aktivitetskortuuid,
        )

        testRapid.sendTestMessage(beriketMelding)
        val etterBerikelse = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, stillingId.toString())
        }
        assertEquals(1, etterBerikelse.size)
        assertEquals(hendelseId, etterBerikelse.single().varselId)
        assertEquals(DelCv.name, etterBerikelse.single().mal.name)
        assertEquals(stillingId.toString(), etterBerikelse.single().avsenderReferanseId)
        assertEquals("12345678901", etterBerikelse.single().mottakerFnr)
        assertEquals("Z123456", etterBerikelse.single().avsenderNavIdent)

        val førsteVarsel = etterBerikelse.single()
        testRapid.sendTestMessage(beriketMelding)
        val etterDuplikat = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, stillingId.toString())
        }
        assertEquals(1, etterDuplikat.size)
        assertEquals(førsteVarsel, etterDuplikat.single())
    }

    @Test
    fun `skal avvise komplett melding uten hendelseId`() {
        val stillingId = UUID.randomUUID()
        val meldingUtenHendelseId = samtykkeForespurtMelding(
            fnr = "12345678901",
            stillingId = stillingId,
            stillingsTittel = "Senior Utvikler",
            forespurtAvIdent = "Z123456",
            hendelseId = null,
        )

        testRapid.sendTestMessage(meldingUtenHendelseId)

        val varsler = dataSource.transaction { tx ->
            MinsideVarsel.hentVarslerForStilling(tx, stillingId.toString())
        }
        assertEquals(0, varsler.size)
    }

    private fun samtykkeForespurtMelding(
        fnr: String,
        stillingId: UUID,
        stillingsTittel: String,
        forespurtAvIdent: String,
        hendelseId: String?,
        aktivitetskortuuid: String? = UUID.randomUUID().toString(),
    ) = objectMapper.createObjectNode().apply {
        put("@event_name", "samtykke-forespurt-om-deling-av-cv")
        put("fnr", fnr)
        put("stillingsId", stillingId.toString())
        put("stillingsTittel", stillingsTittel)
        put("svarfrist", ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS).toString())
        put("forespurtAvIdent", forespurtAvIdent)
        put("forespurtTidspunkt", ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS).toString())
        hendelseId?.let { put("hendelseId", it) }
        aktivitetskortuuid?.let { put("aktivitetskortuuid", it) }
    }.toString()
}