package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.kandidatvarsel.DatabaseConfig
import no.nav.toi.kandidatvarsel.minside.CvDeltMedArbeidsgiver
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel
import no.nav.toi.kandidatvarsel.transaction
import no.nav.toi.kandidatvarsel.util.TestRapid
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KandidatCvDeltMedArbeidsgiverLytterTest {
    private val prodCluster = "prod-gcp"
    private val devCluster = "dev-gcp"
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

        KandidatCvDeltMedArbeidsgiverLytter(testRapid, dataSource, clusterName = prodCluster)
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

    @ParameterizedTest
    @ValueSource(ints = [1, 3])
    fun `skal ikke duplisere varsler ved replay gjennom behov og besvarelse`(antallKandidater: Int) {
        val rapid = TestRapid()
        val aktørIder = fødselsnummerPerAktørId.keys.take(antallKandidater)
        var antallBesvarelser = 0
        KandidatCvDeltMedArbeidsgiverLytter(rapid, dataSource, clusterName = prodCluster)
        River(rapid).apply {
            precondition {
                it.requireValue("@event_name", "kandidat_v2.DelCvMedArbeidsgiver")
                it.requireKey("@behov")
                it.forbid("fodselsnummerliste")
            }
            validate {
                it.require("@behov") { behov ->
                    require(behov.toList().any { it.asString() == "fodselsnummerliste" })
                }
                it.requireKey("aktøridliste", "@id")
            }
        }.register(object : River.PacketListener {
            override fun onPacket(
                packet: JsonMessage,
                context: MessageContext,
                metadata: MessageMetadata,
                meterRegistry: MeterRegistry,
            ) {
                assertEquals(hendelseId, packet["@id"].asString())
                packet["fodselsnummerliste"] = packet["aktøridliste"].toList().associate {
                    val aktørId = it.asString()
                    aktørId to fødselsnummerPerAktørId.getValue(aktørId)
                }
                antallBesvarelser++
                rapid.publish(packet.toJson())
            }
        })
        val hendelse = objectMapper.writeValueAsString(melding(aktørIder, fødselsnummerliste = null))

        rapid.sendTestMessage(hendelse)

        val før = hentVarsler().toSet()
        assertEquals(1, antallBesvarelser)
        assertEquals(antallKandidater, før.size)
        assertEquals(
            aktørIder.map { fødselsnummerPerAktørId.getValue(it) }.toSet(),
            før.map { it.mottakerFnr }.toSet(),
        )
        assertEquals(antallKandidater, før.map { it.varselId }.toSet().size)

        rapid.reset()
        rapid.sendTestMessage(hendelse)

        assertEquals(2, antallBesvarelser)
        assertEquals(før, hentVarsler().toSet())
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
    fun `skal beholde uendrede varsler med stabile varselIder ved replay med endret kandidat-rekkefølge`() {
        val førsteRekkefølge = listOf(kandidatAktørId, "1000000000002", "1000000000003")
        val andreRekkefølge = listOf("1000000000003", kandidatAktørId, "1000000000002")

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding(førsteRekkefølge)))
        val før = hentVarsler()
        assertEquals(3, før.size)
        assertEquals(3, før.map { it.varselId }.toSet().size)
        assertEquals(
            førsteRekkefølge.map { deterministiskVarselId(hendelseId, it) }.toSet(),
            før.map { it.varselId }.toSet()
        )

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding(andreRekkefølge)))
        val etter = hentVarsler()
        assertEquals(3, etter.size)
        assertEquals(
            før.map { it.varselId to it.mottakerFnr }.toSet(),
            etter.map { it.varselId to it.mottakerFnr }.toSet()
        )
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
        assertEquals(hendelseId, behov.path("@id").asText())
        assertEquals(stillingsId, behov.path("stillingsId").asText())
        assertEquals("kandidat_v2.DelCvMedArbeidsgiver", behov.path("@event_name").asText())
    }

    @Test
    fun `skal bevare hendelsesId ved gjentatt publisering av behov`() {
        val melding = objectMapper.writeValueAsString(melding(fødselsnummerliste = null))

        testRapid.sendTestMessage(melding)
        testRapid.sendTestMessage(melding)

        assertEquals(2, testRapid.inspektør.size)
        assertEquals(
            listOf(hendelseId, hendelseId),
            (0 until testRapid.inspektør.size).map { testRapid.inspektør.message(it).path("@id").asText() },
        )
        assertEquals(0, hentVarsler().size)
    }

    @Test
    fun `skal bevare eksisterende behov og hendelsesdata når nytt behov legges til`() {
        val aktørIder = fødselsnummerPerAktørId.keys.toList()
        val melding = melding(aktørIder, fødselsnummerliste = null) + mapOf(
            "@behov" to listOf("annetbehov"),
            "annetbehov" to mapOf("verdi" to "eksisterende svar"),
        )

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(1, testRapid.inspektør.size)
        val behov = testRapid.inspektør.message(0)
        assertEquals(listOf("annetbehov", "fodselsnummerliste"), behov.path("@behov").map { it.asText() })
        assertEquals(aktørIder.toSet(), behov.path("aktøridliste").map { it.asText() }.toSet())
        assertEquals("eksisterende svar", behov.path("annetbehov").path("verdi").asText())
        assertEquals(0, hentVarsler().size)
    }

    @Test
    fun `skal avvise ugyldig behov uten å publisere eller opprette varsler`() {
        val melding = melding(fødselsnummerliste = null) + ("@behov" to "fodselsnummerliste")

        assertThrows<IllegalArgumentException> {
            testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        }

        assertEquals(0, testRapid.inspektør.size)
        assertEquals(0, hentVarsler().size)
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

    @Test
    fun `skal ikke republisere når uløst behov allerede finnes`() {
        val melding = melding(fødselsnummerliste = null) + mapOf(
            "@behov" to listOf("fodselsnummerliste"),
            "aktøridliste" to listOf(kandidatAktørId),
        )

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(0, testRapid.inspektør.size)
        assertEquals(0, hentVarsler().size)
    }

    @Test
    fun `skal avvise manglende fødselsnummer i mapping for kandidat`() {
        val aktørIder = listOf(kandidatAktørId, "1000000000002")
        val melding = melding(
            aktørIder = aktørIder,
            fødselsnummerliste = mapOf(kandidatAktørId to fødselsnummerPerAktørId.getValue(kandidatAktørId)),
        )

        assertThrows<IllegalArgumentException> {
            testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        }
        assertEquals(0, hentVarsler().size)
    }

    @Test
    fun `skal avvise null i fødselsnummermapping`() {
        val aktørIder = listOf(kandidatAktørId, "1000000000002")
        val melding = melding(
            aktørIder = aktørIder,
            fødselsnummerliste = mapOf(
                kandidatAktørId to fødselsnummerPerAktørId.getValue(kandidatAktørId),
                "1000000000002" to null,
            ),
        )
        val rapid = TestRapid()
        KandidatCvDeltMedArbeidsgiverLytter(rapid, dataSource, clusterName = prodCluster)

        assertThrows<IllegalArgumentException> {
            rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        }
        assertEquals(0, hentVarsler().size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["prod-gcp", "dev-gcp"])
    fun `skal avvise fødselsnummermapping med ugyldig format i alle miljø`(clusterName: String) {
        val melding = melding(
            fødselsnummerliste = mapOf(kandidatAktørId to "1000000000001"),
        )
        val rapid = TestRapid()
        KandidatCvDeltMedArbeidsgiverLytter(rapid, dataSource, clusterName = clusterName)

        assertThrows<IllegalArgumentException> {
            rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        }
        assertEquals(0, hentVarsler().size)
    }

    @Test
    fun `skal i dev hoppe over null fødselsnummer og opprette varsel for gyldige kandidater`() {
        val aktørIder = listOf(kandidatAktørId, "1000000000002")
        val melding = melding(
            aktørIder = aktørIder,
            fødselsnummerliste = mapOf(
                kandidatAktørId to fødselsnummerPerAktørId.getValue(kandidatAktørId),
                "1000000000002" to null,
            ),
        )
        val rapid = TestRapid()
        KandidatCvDeltMedArbeidsgiverLytter(rapid, dataSource, clusterName = devCluster)

        assertDoesNotThrow {
            rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        }

        val varsler = hentVarsler()
        assertEquals(1, varsler.size)
        assertEquals(fødselsnummerPerAktørId.getValue(kandidatAktørId), varsler.single().mottakerFnr)
        assertEquals(deterministiskVarselId(hendelseId, kandidatAktørId), varsler.single().varselId)
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `skal i dev ikke opprette varsler når alle fødselsnumre er null`() {
        val aktørIder = listOf(kandidatAktørId, "1000000000002")
        val melding = melding(
            aktørIder = aktørIder,
            fødselsnummerliste = aktørIder.associateWith { null },
        )
        val rapid = TestRapid()
        KandidatCvDeltMedArbeidsgiverLytter(rapid, dataSource, clusterName = devCluster)

        assertDoesNotThrow {
            rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        }

        assertEquals(0, hentVarsler().size)
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `skal i dev beholde stabil varselId ved replay fra delvis null til full mapping`() {
        val aktør1 = kandidatAktørId
        val aktør2 = "1000000000002"
        val aktørIder = listOf(aktør1, aktør2)
        val rapid = TestRapid()
        KandidatCvDeltMedArbeidsgiverLytter(rapid, dataSource, clusterName = devCluster)

        val delvisMelding = melding(
            aktørIder = aktørIder,
            fødselsnummerliste = mapOf(
                aktør1 to fødselsnummerPerAktørId.getValue(aktør1),
                aktør2 to null,
            ),
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(delvisMelding))

        val etterFørste = hentVarsler()
        assertEquals(1, etterFørste.size)
        val førsteVarsel = etterFørste.single()
        assertEquals(fødselsnummerPerAktørId.getValue(aktør1), førsteVarsel.mottakerFnr)
        assertEquals(deterministiskVarselId(hendelseId, aktør1), førsteVarsel.varselId)

        val fullMelding = melding(
            aktørIder = aktørIder,
            fødselsnummerliste = mapOf(
                aktør1 to fødselsnummerPerAktørId.getValue(aktør1),
                aktør2 to fødselsnummerPerAktørId.getValue(aktør2),
            ),
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(fullMelding))

        val etterAndre = hentVarsler()
        assertEquals(2, etterAndre.size)
        assertEquals(
            aktørIder.map { deterministiskVarselId(hendelseId, it) }.toSet(),
            etterAndre.map { it.varselId }.toSet(),
        )
        assertEquals(
            førsteVarsel,
            etterAndre.single { it.mottakerFnr == fødselsnummerPerAktørId.getValue(aktør1) },
        )
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `skal i dev fortsatt avvise manglende fødselsnummer i mapping`() {
        val aktørIder = listOf(kandidatAktørId, "1000000000002")
        val melding = melding(
            aktørIder = aktørIder,
            fødselsnummerliste = mapOf(kandidatAktørId to fødselsnummerPerAktørId.getValue(kandidatAktørId)),
        )
        val rapid = TestRapid()
        KandidatCvDeltMedArbeidsgiverLytter(rapid, dataSource, clusterName = devCluster)

        assertThrows<IllegalArgumentException> {
            rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        }
        assertEquals(0, hentVarsler().size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["kandidater", "stillingsId", "utførtAvNavIdent", "tidspunkt"])
    fun `skal avvise manglende påkrevd felt`(felt: String) {
        val melding = melding() - felt

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(0, testRapid.inspektør.size)
        assertEquals(0, hentVarsler().size)
    }

    @Test
    fun `skal avvise kandidater som array`() {
        val melding = melding() + ("kandidater" to listOf(kandidatAktørId))

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
        fødselsnummerliste: Map<String, Any?>? = aktørIder.associateWith { fødselsnummerPerAktørId.getValue(it) },
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

    private fun deterministiskVarselId(hendelseId: String, aktørId: String): String =
        UUID.nameUUIDFromBytes("$hendelseId:$aktørId".toByteArray(UTF_8)).toString()
}
