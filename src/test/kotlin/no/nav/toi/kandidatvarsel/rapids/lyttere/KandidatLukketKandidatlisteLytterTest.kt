package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.toi.kandidatvarsel.DatabaseConfig
import no.nav.toi.kandidatvarsel.Stilling
import no.nav.toi.kandidatvarsel.StillingClient
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel
import no.nav.toi.kandidatvarsel.minside.StillingsannonseOppdatert
import no.nav.toi.kandidatvarsel.minside.bestillVarsel
import no.nav.toi.kandidatvarsel.transaction
import no.nav.toi.kandidatvarsel.util.TestRapid
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.charset.StandardCharsets.UTF_8
import java.time.ZonedDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KandidatLukketKandidatlisteLytterTest {
    private val postgres = PostgreSQLContainer("postgres:18").apply { start() }
    private val dataSource = DatabaseConfig(
        hostname = postgres.host,
        port = postgres.firstMappedPort,
        database = postgres.databaseName,
        username = postgres.username,
        password = postgres.password,
    ).createDataSource().also { Flyway.configure().dataSource(it).load().migrate() }
    private val rapid = TestRapid().also { KandidatLukketKandidatlisteLytter(it, dataSource, "local") }
    private val mapper = jacksonObjectMapper()
    private val stillingId = "cf43157d-7b67-4c13-b6a2-248df8f89103"
    private val tidspunkt = "2026-10-30T14:15:07.057+01:00"
    private val fødselsnumre = mapOf(
        "1000000000001" to "01019012345",
        "1000000000002" to "02029023456",
        "1000000000003" to "03039034567",
    )
    private val mottakere = fødselsnumre.keys.take(2)

    @BeforeEach
    fun reset() {
        rapid.reset()
        dataSource.transaction { it.sql("DELETE FROM minside_varsel").update() }
    }

    @AfterAll
    fun close() {
        dataSource.close()
        postgres.stop()
    }

    @Test
    fun `varsler bare unike kandidater som ikke fikk jobben og ignorerer ekstra oppslag`() {
        send(melding() + ("aktørIderFikkIkkeJobben" to mottakere + mottakere.first()))

        val varsler = hentVarsler()
        assertEquals(mottakere.map { fødselsnumre.getValue(it) }.toSet(), varsler.map { it.mottakerFnr }.toSet())
        assertEquals(2, varsler.size)
        varsler.forEach {
            assertEquals(StillingsannonseOppdatert, it.mal)
            assertEquals("Z123456", it.avsenderNavIdent)
        }
    }

    @Test
    fun `lukking berikes og bestilles hos minside uten duplikater ved ny levering`() {
        val flytRapid = TestRapid().also {
            KandidatLukketKandidatlisteBehovLytter(it)
            KandidatLukketKandidatlisteLytter(it, dataSource, "local")
        }
        flytRapid.sendTestMessage(mapper.writeValueAsString(melding() - "fodselsnummerliste"))
        assertEquals(0, hentVarsler().size)
        assertEquals(1, flytRapid.inspektør.size)

        val behov = flytRapid.inspektør.message(0)
        assertEquals(mottakere, behov["aktøridliste"].map { it.asText() })
        val beriket = behov.deepCopy<ObjectNode>().apply {
            set<ObjectNode>("fodselsnummerliste", mapper.valueToTree(fødselsnumre))
        }
        flytRapid.sendTestMessage(beriket.toString())
        flytRapid.sendTestMessage(beriket.toString())
        assertEquals(2, hentVarsler().size)
        assertEquals(1, flytRapid.inspektør.size)

        val stillingClient = object : StillingClient {
            override fun getStilling(stillingsId: UUID): Stilling {
                assertEquals(UUID.fromString(stillingId), stillingsId)
                return Stilling("Utvikler", "Arbeidsgiver AS")
            }
        }
        val producer = MockProducer(true, null, StringSerializer(), StringSerializer())
        producer.use {
            assertTrue(bestillVarsel(dataSource, stillingClient, it))
            assertTrue(bestillVarsel(dataSource, stillingClient, it))
            assertFalse(bestillVarsel(dataSource, stillingClient, it))
            val bestillinger = it.history().map { record -> mapper.readTree(record.value()) }
            assertEquals(
                mottakere.map { aktørId -> fødselsnumre.getValue(aktørId) }.toSet(),
                bestillinger.map { bestilling -> bestilling["ident"].asText() }.toSet(),
            )
            bestillinger.forEach { bestilling ->
                assertEquals(
                    "Nav har oppdatert stillingsannonsen «Utvikler» hos Arbeidsgiver AS. Åpne annonsen for å se oppdateringen.",
                    bestilling["tekster"].single()["tekst"].asText(),
                )
            }
        }
    }

    @Test
    fun `samme lukking med ny meldingsId og tidssoneformatering gir ikke nye varsler`() {
        send(melding())
        val før = hentVarsler().toSet()
        send(melding() + mapOf(
            "@id" to UUID.randomUUID().toString(),
            "kandidatlisteId" to UUID.randomUUID().toString(),
            "tidspunkt" to "2026-10-30T13:15:07.057Z",
        ))
        assertEquals(før, hentVarsler().toSet())
    }

    @Test
    fun `retry etter delvis behandling bevarer eksisterende varsel og oppretter resten`() {
        dataSource.transaction { tx ->
            MinsideVarsel.create(
                mal = StillingsannonseOppdatert,
                avsenderReferanseId = stillingId,
                mottakerFnr = fødselsnumre.getValue(mottakere.first()),
                avsenderNavident = "Z123456",
                varselId = UUID.nameUUIDFromBytes(
                    "${StillingsannonseOppdatert.name}:$stillingId:${mottakere.first()}:${ZonedDateTime.parse(tidspunkt).toInstant()}".toByteArray(UTF_8),
                ).toString(),
            ).markerBestilt().insert(tx)
        }
        val eksisterende = hentVarsler().single()

        send(melding())

        assertEquals(2, hentVarsler().size)
        assertEquals(eksisterende, hentVarsler().single { it.varselId == eksisterende.varselId })
    }

    @Test
    fun `nytt lukkingstidspunkt gir nye varsler`() {
        send(melding())
        send(melding() + ("tidspunkt" to "2026-10-31T14:15:07.057+01:00"))
        assertEquals(4, hentVarsler().size)
    }

    @Test
    fun `validerer alle fødselsnumre før første varsel lagres`() {
        listOf(
            fødselsnumre - mottakere.last(),
            fødselsnumre + (mottakere.last() to "ugyldig"),
            fødselsnumre + (mottakere.last() to null),
        ).forEach { oppslag ->
            assertThrows<IllegalArgumentException> { send(melding() + ("fodselsnummerliste" to oppslag)) }
            assertEquals(0, hentVarsler().size)
        }
    }

    @Test
    fun `dev hopper over null og oppretter bare manglende varsel når fødselsnummer blir tilgjengelig`() {
        val devRapid = TestRapid().also { KandidatLukketKandidatlisteLytter(it, dataSource, "dev-gcp") }
        devRapid.sendTestMessage(mapper.writeValueAsString(
            melding() + ("fodselsnummerliste" to (fødselsnumre + (mottakere.last() to null))),
        ))
        val eksisterende = hentVarsler().single()

        devRapid.sendTestMessage(mapper.writeValueAsString(melding()))

        assertEquals(2, hentVarsler().size)
        assertEquals(eksisterende, hentVarsler().single { it.varselId == eksisterende.varselId })
    }

    @Test
    fun `tom liste og melding uten berikelse oppretter ingen varsler`() {
        send(melding() + ("aktørIderFikkIkkeJobben" to emptyList<String>()))
        send(melding() - "fodselsnummerliste")
        assertEquals(0, hentVarsler().size)
    }

    private fun hentVarsler() = dataSource.transaction { MinsideVarsel.hentVarslerForStilling(it, stillingId) }
    private fun send(melding: Map<String, Any?>) = rapid.sendTestMessage(mapper.writeValueAsString(melding))
    private fun melding(): Map<String, Any?> = mapOf(
        "@event_name" to "kandidat_v2.LukketKandidatliste",
        "@id" to UUID.randomUUID().toString(),
        "stillingsId" to stillingId,
        "utførtAvNavIdent" to "Z123456",
        "tidspunkt" to tidspunkt,
        "aktørIderFikkIkkeJobben" to mottakere,
        "aktørIderFikkJobben" to listOf(fødselsnumre.keys.last()),
        "fodselsnummerliste" to fødselsnumre,
    )
}
