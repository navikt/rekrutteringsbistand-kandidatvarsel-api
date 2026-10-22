package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.toi.kandidatvarsel.DatabaseConfig
import no.nav.toi.kandidatvarsel.minside.CvDeltMedArbeidsgiver
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel
import no.nav.toi.kandidatvarsel.transaction
import no.nav.toi.kandidatvarsel.util.TestRapid
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KandidatCvDeltMedArbeidsgiverLytterTest {
    private val postgres = PostgreSQLContainer("postgres:18").apply { start() }
    private val dataSource = DatabaseConfig(
        hostname = postgres.host,
        port = postgres.firstMappedPort,
        database = postgres.databaseName,
        username = postgres.username,
        password = postgres.password
    ).createDataSource()

    private val testRapid = TestRapid()
    private val objectMapper = jacksonObjectMapper()
    private val kandidatAktørId = "1000000000001"
    private val fødselsnummerPerAktørId = mapOf(
        kandidatAktørId to "01019012345",
        "1000000000002" to "02029023456",
        "1000000000003" to "03039034567",
    )
    private val stillingsId = "cf43157d-7b67-4c13-b6a2-248df8f89103"
    private val navIdent = "Z123456"
    private val hendelseId = "c5482334-9a9a-44b5-bb47-cd314ea45ec0"

    @BeforeAll
    fun oppsett() {
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()

        KandidatCvDeltMedArbeidsgiverLytter(testRapid, dataSource)
    }

    @BeforeEach
    fun reset() {
        testRapid.reset()
        dataSource.transaction { tx ->
            tx.sql("DELETE FROM minside_varsel").update()
        }
    }

    @AfterAll
    fun ryddOpp() {
        dataSource.close()
        postgres.stop()
    }

    @Test
    fun `skal opprette varsel med fnr for kandidat angitt med aktørId`() {
        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding()))

        val varsler = hentVarsler()
        assertEquals(1, varsler.size)
        assertEquals(CvDeltMedArbeidsgiver, varsler.single().mal)
        assertEquals(fødselsnummerPerAktørId.getValue(kandidatAktørId), varsler.single().mottakerFnr)
        assertEquals(navIdent, varsler.single().avsenderNavIdent)
        assertEquals(stillingsId, varsler.single().avsenderReferanseId)
        assertEquals(hendelseId, varsler.single().varselId)
    }

    @Test
    fun `skal beholde ett uendret varsel når samme hendelse mottas på nytt`() {
        val melding = objectMapper.writeValueAsString(melding())
        testRapid.sendTestMessage(melding)
        val før = hentVarsler()
        assertEquals(1, før.size)
        assertEquals(fødselsnummerPerAktørId.getValue(kandidatAktørId), før.single().mottakerFnr)

        testRapid.sendTestMessage(melding)
        val etter = hentVarsler()

        assertEquals(1, etter.size)
        assertEquals(før.single(), etter.single())
        assertEquals(hendelseId, etter.single().varselId)
    }

    @Test
    fun `skal opprette separate varsler for samme kandidat med ulike hendelseIder`() {
        val hendelseIder = listOf(hendelseId, UUID.randomUUID().toString())
        hendelseIder.forEach { id ->
            testRapid.sendTestMessage(objectMapper.writeValueAsString(melding() + ("@id" to id)))
        }

        val varsler = hentVarsler()
        assertEquals(2, varsler.size)
        assertEquals(hendelseIder.toSet(), varsler.map { it.varselId }.toSet())
        varsler.forEach {
            assertEquals(CvDeltMedArbeidsgiver, it.mal)
            assertEquals(fødselsnummerPerAktørId.getValue(kandidatAktørId), it.mottakerFnr)
            assertEquals(navIdent, it.avsenderNavIdent)
            assertEquals(stillingsId, it.avsenderReferanseId)
        }
    }

    @Test
    fun `skal opprette varsel for hver aktørId i kandidatobjektet`() {
        val aktørIder = listOf(kandidatAktørId, "1000000000002", "1000000000003")

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding(aktørIder)))

        val varsler = hentVarsler()
        assertEquals(aktørIder.size, varsler.size)
        assertEquals(
            aktørIder.map { fødselsnummerPerAktørId.getValue(it) }.toSet(),
            varsler.map { it.mottakerFnr }.toSet(),
        )
        varsler.forEach {
            assertEquals(stillingsId, it.avsenderReferanseId)
            assertEquals(CvDeltMedArbeidsgiver, it.mal)
        }
    }

    @Test
    fun `skal ignorere annet hendelsesnavn`() {
        val melding = melding() + ("@event_name" to "kandidat_v2.AnnenHendelse")

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(0, testRapid.inspektør.size)
        assertEquals(0, hentVarsler().size)
    }

    @Test
    fun `skal publisere behov uten å opprette varsler når fødselsnummerliste mangler`() {
        val melding = melding(fødselsnummerliste = null)
        assertFalse(melding.containsKey("fnr"))
        assertFalse(melding.containsKey("aktivitetskortuuid"))
        assertFalse(melding.containsKey("fodselsnummerliste"))

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(0, hentVarsler().size)
        assertEquals(1, testRapid.inspektør.size)
        val behov = testRapid.inspektør.message(0)
        assertTrue(behov.path("@behov").isArray)
        assertTrue(behov.path("@behov").map { it.asText() }.contains("fodselsnummerliste"))
        assertEquals(listOf(kandidatAktørId), behov.path("aktøridliste").map { it.asText() })
        assertFalse(behov.has("fodselsnummerliste"))
    }

    @Test
    fun `skal ikke publisere behov når fødselsnummerliste allerede er populert`() {
        val melding = melding(
            fødselsnummerliste = mapOf(kandidatAktørId to fødselsnummerPerAktørId.getValue(kandidatAktørId)),
        )

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(0, testRapid.inspektør.size)
        val varsler = hentVarsler()
        assertEquals(1, varsler.size)
        assertEquals(fødselsnummerPerAktørId.getValue(kandidatAktørId), varsler.single().mottakerFnr)
    }

    @ParameterizedTest
    @ValueSource(strings = ["kandidater", "stillingsId", "utførtAvNavIdent", "tidspunkt"])
    fun `skal avvise manglende påkrevd felt`(felt: String) {
        val melding = melding() - felt

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(0, testRapid.inspektør.size)
        assertEquals(0, hentVarsler().size)
    }

    @ParameterizedTest
    @CsvSource(
        "stillingsId, ugyldig-uuid",
        "tidspunkt, ugyldig-tidspunkt",
        "tidspunkt, 2026-09-24T10:15:30",
    )
    fun `skal avvise ugyldig UUID eller tidspunkt`(felt: String, verdi: String) {
        val melding = melding() + (felt to verdi)

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(0, testRapid.inspektør.size)
        assertEquals(0, hentVarsler().size)
    }

    private fun hentVarsler() = dataSource.transaction { tx ->
        MinsideVarsel.hentVarslerForStilling(tx, stillingsId)
    }

    private fun melding(
        aktørIder: List<String> = listOf(kandidatAktørId),
        fødselsnummerliste: Map<String, String>? = aktørIder.associateWith { fødselsnummerPerAktørId.getValue(it) },
    ): Map<String, Any?> = mapOf(
        "@event_name" to "kandidat_v2.DelCvMedArbeidsgiver",
        "@id" to hendelseId,
        "organisasjonsnummer" to "999999999",
        "kandidatlisteId" to "f7cdf77e-3f12-42d0-ab31-ad9402879a4c",
        "stillingsId" to stillingsId,
        "utførtAvNavIdent" to navIdent,
        "utførtAvNavKontorKode" to "0403",
        "meldingTilArbeidsgiver" to "",
        "tidspunkt" to "2026-10-20T14:19:42.263+02:00",
        "kandidater" to aktørIder.associateWith {
            mapOf(
                "harHullICv" to true,
                "alder" to 30,
                "innsatsbehov" to "HAR_IKKE_GJELDENDE_14A_VEDTAK",
                "hovedmål" to "HAR_IKKE_GJELDENDE_14A_VEDTAK",
            )
        },
    ) + if (fødselsnummerliste != null) mapOf("fodselsnummerliste" to fødselsnummerliste) else emptyMap()
}
