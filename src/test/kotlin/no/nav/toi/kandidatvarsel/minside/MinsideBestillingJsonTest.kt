package no.nav.toi.kandidatvarsel.minside

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Test av JSON-en som tms-varsel-builderen legger på min-side.aapen-brukervarsel-v1.
 * Låser formatet slik at en oppgradering av no.nav.tms.varsel:kotlin-builder ikke endrer meldingen ubemerket.
 */
class MinsideBestillingJsonTest {
    private val objectMapper = jacksonObjectMapper()
    private val varselId = "0199a0f0-0000-7000-8000-000000000001"

    @Test
    fun `bestilling for stillingsvarsel har forventet format`() {
        val mal = VurdertSomAktuell
        val json = sendOgLes { it.sendBestilling(varsel(mal, "stilling-1"), mal, "Tittel", "Arbeidsgiver AS") }

        assertFellesFelter(json)
        assertThat(json["link"].asText()).isEqualTo("https://www.nav.no/arbeid/stilling/stilling-1")
        assertThat(json["tekster"].single()["tekst"].asText()).isEqualTo(mal.minsideTekst("Tittel", "Arbeidsgiver AS"))
        assertEksternVarsling(json["eksternVarsling"], mal.smsTekst(), mal.epostTittel(), mal.epostHtmlBody())
    }

    @Test
    fun `bestilling for rekrutteringstreffvarsel har forventet format`() {
        val mal = KandidatInvitertTreff
        val json = sendOgLes { it.sendBestilling(varsel(mal, "treff-1"), mal) }

        assertFellesFelter(json)
        assertThat(json["link"].asText()).isEqualTo(mal.lenkeurl("treff-1", false))
        assertThat(json["tekster"].single()["tekst"].asText()).isEqualTo(mal.minsideTekst())
        assertEksternVarsling(json["eksternVarsling"], mal.smsTekst(), mal.epostTittel(), mal.epostHtmlBody())
    }

    /**
     * Builderen validerer innholdet (lengder, lenker i SMS/e-post, nav.no-domene) og kaster
     * VarselValidationException ved brudd. Alle maler må derfor kunne bygges.
     */
    @Test
    fun `alle maler kan bygges og valideres av builderen`() {
        val alleEndringer = EndringFlettedata.entries.map { it.displayTekst }

        VarselType.entries.flatMap { Maler.malerForVarselType(it) }.map(Maler::valueOf).forEach { mal ->
            val json = when (mal) {
                is StillingMal -> sendOgLes {
                    it.sendBestilling(varsel(mal, "stilling-1"), mal, "Tittel", "Arbeidsgiver AS")
                }
                is RekrutteringstreffMal -> sendOgLes {
                    it.sendBestilling(varsel(mal, "treff-1").copy(flettedata = alleEndringer), mal)
                }
            }
            assertThat(json["varselId"].asText()).`as`(mal.name).isEqualTo(varselId)
        }
    }

    private fun assertFellesFelter(json: JsonNode) {
        assertThat(json.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "@event_name", "type", "varselId", "ident", "sensitivitet", "link", "tekster",
            "eksternVarsling", "aktivFremTil", "produsent", "metadata",
        )
        assertThat(json["@event_name"].asText()).isEqualTo("opprett")
        assertThat(json["type"].asText()).isEqualTo("beskjed")
        assertThat(json["varselId"].asText()).isEqualTo(varselId)
        assertThat(json["ident"].asText()).isEqualTo("12345678901")
        assertThat(json["sensitivitet"].asText()).isEqualTo("substantial")

        val tekst = json["tekster"].single()
        assertThat(tekst["spraakkode"].asText()).isEqualTo("nb")
        assertThat(tekst["default"].asBoolean()).isTrue()

        assertThat(ZonedDateTime.parse(json["aktivFremTil"].asText()).toInstant())
            .isCloseTo(ZonedDateTime.now(ZoneOffset.UTC).plusWeeks(10).toInstant(), within(1, ChronoUnit.MINUTES))

        val produsent = json["produsent"]
        assertThat(produsent["cluster"].asText()).isEqualTo("local")
        assertThat(produsent["namespace"].asText()).isEqualTo("toi")
        assertThat(produsent["appnavn"].asText()).isEqualTo("kandidatvarsel-api")

        assertThat(json["metadata"]["builder_lang"].asText()).isEqualTo("kotlin")
        assertThat(json["metadata"]["version"].asText()).isNotBlank()
    }

    private fun assertEksternVarsling(ekstern: JsonNode, sms: String, epostTittel: String, epostTekst: String) {
        assertThat(ekstern.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "prefererteKanaler", "smsVarslingstekst", "epostVarslingstittel", "epostVarslingstekst",
        )
        assertThat(ekstern["prefererteKanaler"].map { it.asText() }).containsExactly("SMS")
        assertThat(ekstern["smsVarslingstekst"].asText()).isEqualTo(sms)
        assertThat(ekstern["epostVarslingstittel"].asText()).isEqualTo(epostTittel)
        assertThat(ekstern["epostVarslingstekst"].asText()).isEqualTo(epostTekst)
    }

    private fun varsel(mal: Mal, avsenderReferanseId: String) = MinsideVarsel(
        dbid = null,
        mal = mal,
        varselId = varselId,
        avsenderReferanseId = avsenderReferanseId,
        opprettet = LocalDateTime.now(),
        mottakerFnr = "12345678901",
        avsenderNavIdent = "Z000001",
        bestilt = false,
        minsideStatus = null,
        eksternStatus = null,
        eksternKanal = null,
        eksternFeilmelding = null,
    )

    private fun sendOgLes(send: (MockProducer<String, String>) -> Unit): JsonNode {
        val producer = MockProducer(true, null, StringSerializer(), StringSerializer())
        send(producer)
        val record = producer.history().single()
        assertThat(record.topic()).isEqualTo(BESTILLING_TOPIC)
        assertThat(record.key()).isEqualTo(varselId)
        return objectMapper.readTree(record.value())
    }
}
