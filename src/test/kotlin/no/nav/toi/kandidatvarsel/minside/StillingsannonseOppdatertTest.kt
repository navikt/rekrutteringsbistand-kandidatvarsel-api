package no.nav.toi.kandidatvarsel.minside

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StillingsannonseOppdatertTest {
    @Test
    fun `bestilling for oppdatert stillingsannonse har forventet innhold`() {
        val mal = Maler.valueOf("STILLINGSANNONSE_OPPDATERT")
        val stillingId = "stilling-1"
        val tittel = "Utvikler"
        val arbeidsgiver = "Arbeidsgiver AS"
        val varsel = MinsideVarsel.create(
            mal = mal,
            avsenderReferanseId = stillingId,
            mottakerFnr = "12345678901",
            avsenderNavident = "Z000001",
            varselId = "0199a0f0-0000-7000-8000-000000000001",
        )
        val producer = MockProducer(true, null, StringSerializer(), StringSerializer())

        assertThat(Maler.malerForVarselType(VarselType.STILLING)).contains(mal.name)

        producer.sendBestilling(varsel, mal as StillingMal, tittel, arbeidsgiver)

        val record = producer.history().single()
        assertThat(record.topic()).isEqualTo(BESTILLING_TOPIC)
        val json = jacksonObjectMapper().readTree(record.value())
        assertThat(json["tekster"].single()["tekst"].asText()).isEqualTo(
            "Nav har oppdatert stillingsannonsen «Utvikler» hos Arbeidsgiver AS. Åpne annonsen for å se oppdateringen."
        )
        assertThat(json["link"].asText()).endsWith("/arbeid/stilling/$stillingId")

        val eksternVarsling = json["eksternVarsling"]
        assertThat(eksternVarsling["smsVarslingstekst"].asText())
            .isEqualTo("Nav har oppdatert en stillingsannonse")
        val epost = eksternVarsling["epostVarslingstekst"].asText()
        assertThat(epost).doesNotContain("http", "<a", "nav.no")
    }
}
