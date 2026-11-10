package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.toi.kandidatvarsel.util.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class KandidatLukketKandidatlisteBehovLytterTest {
    private val rapid = TestRapid().also { KandidatLukketKandidatlisteBehovLytter(it) }
    private val mapper = jacksonObjectMapper()
    private val mottakere = listOf("1000000000001", "1000000000002")
    private val fikkJobben = "1000000000003"

    @Test
    fun `ber om fødselsnumre og samtykke for unike kandidater som ikke fikk jobben`() {
        val melding = melding() + mapOf(
            "aktørIderFikkIkkeJobben" to mottakere + mottakere.first(),
            "@behov" to listOf("annetbehov"),
            "annetbehov" to "eksisterende svar",
        )
        send(melding)

        assertEquals(1, rapid.inspektør.size)
        val behov = rapid.inspektør.message(0)
        assertFalse(behov.has("aktøridliste"))
        assertEquals(mottakere, behov["aktørIder"].map { it.asText() })
        assertEquals(listOf("annetbehov", "fodselsnummerliste", "aktørIdSamtykkeStatusListe"), behov["@behov"].map { it.asText() })
        assertEquals("eksisterende svar", behov["annetbehov"].asText())
        assertEquals(listOf(fikkJobben), behov["aktørIderFikkJobben"].map { it.asText() })
    }

    @Test
    fun `publiserer behov uten eksisterende behovsliste`() {
        send(melding())
        assertEquals(1, rapid.inspektør.size)
        assertEquals(listOf("fodselsnummerliste", "aktørIdSamtykkeStatusListe"), rapid.inspektør.message(0)["@behov"].map { it.asText() })
    }

    @Test
    fun `publiserer behov med stillingsId som key`() {
        val stillingsIder = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        stillingsIder.forEach { stillingsId ->
            send(melding() + ("stillingsId" to stillingsId))
        }

        assertEquals(stillingsIder.size, rapid.inspektør.size)
        assertEquals(stillingsIder, (0 until rapid.inspektør.size).map { rapid.inspektør.key(it) })
    }

    @Test
    fun `ber ikke om behov på nytt når det er etterspurt eller besvart`() {
        send(melding() + ("@behov" to listOf("fodselsnummerliste", "aktørIdSamtykkeStatusListe")))
        send(melding() + mapOf(
            "fodselsnummerliste" to mottakere.associateWith { "01019012345" },
            "aktørIdSamtykkeStatusListe" to emptyList<Any>(),
        ))
        assertEquals(0, rapid.inspektør.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["fodselsnummerliste", "aktørIdSamtykkeStatusListe"])
    fun `legger bare til manglende behov og bevarer eksisterende svar`(eksisterendeBehov: String) {
        val svar: JsonNode = if (eksisterendeBehov == "fodselsnummerliste") {
            mapper.valueToTree(mottakere.associateWith { "01019012345" })
        } else {
            mapper.valueToTree(mottakere.map { mapOf("aktørId" to it, "samtykkeStatus" to "IKKE_SVART") })
        }
        send(melding() + mapOf("@behov" to listOf(eksisterendeBehov), eksisterendeBehov to svar))

        assertEquals(1, rapid.inspektør.size)
        val behov = rapid.inspektør.message(0)
        assertEquals(svar, behov[eksisterendeBehov])
        assertEquals(
            setOf("fodselsnummerliste", "aktørIdSamtykkeStatusListe"),
            behov["@behov"].map { it.asText() }.toSet(),
        )
        assertEquals(2, behov["@behov"].size())
    }

    @Test
    fun `tom mottakerliste og andre hendelser gir ingen behov`() {
        send(melding() + ("aktørIderFikkIkkeJobben" to emptyList<String>()))
        send(melding() + ("@event_name" to "kandidat_v2.AnnenHendelse"))
        assertEquals(0, rapid.inspektør.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["stillingsId", "tidspunkt", "utførtAvNavIdent", "aktørIderFikkIkkeJobben"])
    fun `avviser manglende påkrevde felt`(felt: String) {
        send(melding() - felt)
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `avviser ugyldige identifikatorer tidspunkt og mottakerlister`() {
        listOf(
            "stillingsId" to "ugyldig",
            "tidspunkt" to "2026-10-30T12:15:00",
            "utførtAvNavIdent" to "",
            "aktørIderFikkIkkeJobben" to "1000000000001",
            "aktørIderFikkIkkeJobben" to listOf(1000000000001L),
        ).forEach { (felt, verdi) -> send(melding() + (felt to verdi)) }
        assertEquals(0, rapid.inspektør.size)
    }

    private fun send(melding: Map<String, Any?>) = rapid.sendTestMessage(mapper.writeValueAsString(melding))

    private fun melding(): Map<String, Any?> = mapOf(
        "@event_name" to "kandidat_v2.LukketKandidatliste",
        "@id" to "c5482334-9a9a-44b5-bb47-cd314ea45ec0",
        "stillingsId" to "cf43157d-7b67-4c13-b6a2-248df8f89103",
        "utførtAvNavIdent" to "Z123456",
        "tidspunkt" to "2026-10-30T14:15:07.057+01:00",
        "aktørIderFikkIkkeJobben" to mottakere,
        "aktørIderFikkJobben" to listOf(fikkJobben),
    )
}
