package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.toi.kandidatvarsel.util.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class KandidatCvDeltMedArbeidsgiverBehovLytterTest {
    private val testRapid = TestRapid().also { KandidatCvDeltMedArbeidsgiverBehovLytter(it) }
    private val objectMapper = jacksonObjectMapper()
    private val aktørIder = listOf("1000000000001", "1000000000002")
    private val hendelseId = "c5482334-9a9a-44b5-bb47-cd314ea45ec0"

    @Test
    fun `skal publisere behov og bevare den opprinnelige hendelsen uten behov`() {
        val melding = melding()

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(1, testRapid.inspektør.size)
        val forventet = melding + mapOf(
            "@behov" to listOf("fodselsnummerliste"),
            "aktøridliste" to aktørIder,
        )
        val behov = testRapid.inspektør.message(0)
        forventet.forEach { (felt, verdi) ->
            assertEquals(objectMapper.valueToTree(verdi), behov.path(felt), felt)
        }
        assertFalse(behov.has("fodselsnummerliste"))
    }

    @Test
    fun `skal legge til behov når behovslisten er tom`() {
        val melding = melding() + ("@behov" to emptyList<String>())

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(1, testRapid.inspektør.size)
        assertEquals(
            listOf("fodselsnummerliste"),
            testRapid.inspektør.message(0).path("@behov").map { it.asText() },
        )
    }

    @Test
    fun `skal bevare hendelsesId ved gjentatt publisering av behov`() {
        val melding = objectMapper.writeValueAsString(melding())

        testRapid.sendTestMessage(melding)
        testRapid.sendTestMessage(melding)

        assertEquals(2, testRapid.inspektør.size)
        assertEquals(
            listOf(hendelseId, hendelseId),
            (0 until testRapid.inspektør.size).map { testRapid.inspektør.message(it).path("@id").asText() },
        )
    }

    @Test
    fun `skal bevare eksisterende behov og svar når nytt behov legges til`() {
        val melding = melding() + mapOf(
            "@behov" to listOf("annetbehov"),
            "annetbehov" to mapOf("verdi" to "eksisterende svar"),
        )

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(1, testRapid.inspektør.size)
        val behov = testRapid.inspektør.message(0)
        assertEquals(listOf("annetbehov", "fodselsnummerliste"), behov.path("@behov").map { it.asText() })
        assertEquals("eksisterende svar", behov.path("annetbehov").path("verdi").asText())
    }

    @Test
    fun `skal ikke publisere behov når fødselsnummerliste finnes`() {
        val melding = melding() + ("fodselsnummerliste" to mapOf(aktørIder.first() to "01019012345"))

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(0, testRapid.inspektør.size)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun `skal filtrere bort hendelser som allerede har fødselsnummerbehov`(posisjon: Int) {
        val behov = mutableListOf("annetbehov").apply { add(posisjon, "fodselsnummerliste") }
        val melding = melding() + mapOf(
            "@behov" to behov,
            "aktøridliste" to aktørIder,
        )

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(0, testRapid.inspektør.size)
    }

    @Test
    fun `skal ignorere annet hendelsesnavn`() {
        val melding = melding() + ("@event_name" to "kandidat_v2.AnnenHendelse")

        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        assertEquals(0, testRapid.inspektør.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["kandidater", "stillingsId", "utførtAvNavIdent", "tidspunkt"])
    fun `skal avvise manglende påkrevd felt`(felt: String) {
        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding() - felt))

        assertEquals(0, testRapid.inspektør.size)
    }

    @ParameterizedTest
    @CsvSource(
        "stillingsId, ugyldig-uuid",
        "@id, ugyldig-uuid",
        "tidspunkt, ugyldig-tidspunkt",
        "tidspunkt, 2026-09-24T10:15:30",
    )
    fun `skal avvise ugyldig UUID eller tidspunkt`(felt: String, verdi: String) {
        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding() + (felt to verdi)))

        assertEquals(0, testRapid.inspektør.size)
    }

    @Test
    fun `skal avvise kandidater som array`() {
        testRapid.sendTestMessage(objectMapper.writeValueAsString(melding() + ("kandidater" to aktørIder)))

        assertEquals(0, testRapid.inspektør.size)
    }

    private fun melding(): Map<String, Any?> = mapOf(
        "@event_name" to "kandidat_v2.DelCvMedArbeidsgiver",
        "@id" to hendelseId,
        "stillingsId" to "cf43157d-7b67-4c13-b6a2-248df8f89103",
        "utførtAvNavIdent" to "Z123456",
        "tidspunkt" to "2026-10-20T14:19:42.263+02:00",
        "kandidater" to aktørIder.associateWith { mapOf("harHullICv" to true) },
    )
}
