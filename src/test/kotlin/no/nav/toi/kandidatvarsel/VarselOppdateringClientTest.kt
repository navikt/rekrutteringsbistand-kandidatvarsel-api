package no.nav.toi.kandidatvarsel

import no.nav.toi.kandidatvarsel.minside.OPPDATERING_TOPIC
import no.nav.toi.kandidatvarsel.minside.pollOppdateringer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

private const val PARTITION = 10
private val topicPartition = TopicPartition(OPPDATERING_TOPIC, PARTITION)
private val topicPartitionSet = setOf(topicPartition)

class VarselOppdateringClientTest {
    @Test
    fun `Konsumerer alle eksempelhendelser og commiter offset`() {
        val consumer = createMockConsumer()
        val hendelser = Eksempel.alleHendelser
        hendelser.forEachIndexed { index, json ->
            consumer.addRecord(index.toLong(), json)
        }

        var antallOppdateringer = 0
        consumer.pollOppdateringer { oppdateringer ->
            antallOppdateringer += oppdateringer.count()
        }
        assertEquals(hendelser.size, antallOppdateringer)
        assertEquals(hendelser.size, consumer.committed())
    }

    @Test
    fun `Flere varsler i samme batch bevarer offset-rekkefølge`() {
        val consumer = createMockConsumer()
        val varselId1 = UUID.randomUUID().toString()
        val varselId2 = UUID.randomUUID().toString()

        // Interleaved meldinger for to forskjellige varsler
        consumer.addRecord(0, varselId1, Eksempel.opprettet(varselId1))
        consumer.addRecord(1, varselId2, Eksempel.opprettet(varselId2))
        consumer.addRecord(2, varselId1, Eksempel.bestilt(varselId1))
        consumer.addRecord(3, varselId2, Eksempel.bestilt(varselId2))
        consumer.addRecord(4, varselId1, Eksempel.smsSendt(varselId1))

        val mottattOffsets = mutableListOf<Long>()
        consumer.pollOppdateringer { oppdateringer ->
            oppdateringer.forEach { mottattOffsets.add(it.offset) }
        }

        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), mottattOffsets)
    }

    @Test
    fun `partitionOffset formateres som partisjon kolon offset`() {
        val consumer = createMockConsumer()
        consumer.addRecord(42, Eksempel.OPPRETTET)

        consumer.pollOppdateringer { oppdateringer ->
            val oppdatering = oppdateringer.first()
            assertEquals(PARTITION, oppdatering.partition)
            assertEquals(42L, oppdatering.offset)
            assertEquals("$PARTITION:42", oppdatering.partitionOffset)
        }
    }

    @Test
    fun `Feil i prosesseringen ved første poll fører ikke til commit`() {
        val consumer = createMockConsumer()
        consumer.addRecord(0, Eksempel.OPPRETTET)

        assertThrows<RuntimeException> {
            consumer.pollOppdateringer {
                throw RuntimeException()
            }
        }

        assertNull(consumer.committed())
    }

    @Test
    fun `Feil i prosesseringen ved andre poll fører til uendret commit`() {
        val consumer = createMockConsumer()

        /* Første commit går greit */
        consumer.addRecord(0, Eksempel.OPPRETTET)
        consumer.pollOppdateringer {
            // do nothing
        }
        assertEquals(1, consumer.committed())

        /* Andre poll feiler og commit er uendret. */
        consumer.addRecord(1, Eksempel.OPPRETTET)
        assertThrows<RuntimeException> {
            consumer.pollOppdateringer {
                throw RuntimeException()
            }
        }
        assertEquals(1, consumer.committed())
    }

    private fun createMockConsumer() = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST).apply {
        assign(topicPartitionSet)
        updateBeginningOffsets(mapOf(topicPartition to 0L))
        updateEndOffsets(mapOf(topicPartition to 0L))
    }

    private fun MockConsumer<String, String>.addRecord(offset: Long, json: String) =
        addRecord(offset, UUID.randomUUID().toString(), json)

    private fun MockConsumer<String, String>.addRecord(offset: Long, key: String, json: String) =
        addRecord(
            ConsumerRecord(
                OPPDATERING_TOPIC,
                PARTITION,
                offset,
                key,
                json,
            )
        )

    private fun MockConsumer<String, String>.committed(): Int? =
        committed(setOf(topicPartition))[topicPartition]?.offset()?.toInt()

}

private object Eksempel {
    fun opprettet(varselId: String) = """
        {
          "@event_name": "opprettet",
          "varseltype": "beskjed",
          "varselId": "$varselId",
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    fun bestilt(varselId: String) = """
        {
          "@event_name": "eksternStatusOppdatert",
          "status": "bestilt",
          "varseltype": "beskjed",
          "varselId": "$varselId",
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    fun smsSendt(varselId: String) = """
        {
          "@event_name": "eksternStatusOppdatert",
          "status": "sendt",
          "varseltype": "beskjed",
          "varselId": "$varselId",
          "kanal": "SMS",
          "renotifikasjon": false,
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    const val OPPRETTET = """
        {
          "@event_name": "opprettet",
          "varseltype": "oppgave",
          "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    const val INAKTIVERT = """
        {
          "@event_name": "inaktivert",
          "varseltype": "oppgave",
          "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    const val SLETTET = """
        {
          "@event_name": "slettet",
          "varseltype": "oppgave",
          "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    const val BESTILT = """
        {
          "@event_name": "eksternStatusOppdatert",
          "status": "bestilt",
          "varseltype": "oppgave",
          "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    const val VENTER = """
        {
          "@event_name": "eksternStatusOppdatert",
          "status": "venter",
          "varseltype": "oppgave",
          "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    const val KANSELLERT = """
        {
          "@event_name": "eksternStatusOppdatert",
          "status": "kansellert",
          "varseltype": "oppgave",
          "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    const val SMS_SENDT = """
        {
          "@event_name": "eksternStatusOppdatert",
          "status": "sendt",
          "varseltype": "oppgave",
          "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
          "kanal": "SMS",
          "renotifikasjon": false,
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """


    const val SMS_FERDIGSTILT = """
        {
          "@event_name": "eksternStatusOppdatert",
          "status": "ferdigstilt",
          "varseltype": "oppgave",
          "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
          "kanal": "SMS",
          "renotifikasjon": false,
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    const val FEILET = """
        {
          "@event_name": "eksternStatusOppdatert",
          "status": "feilet",
          "varseltype": "oppgave",
          "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
          "feilmelding": "mottaker har reservert seg mot digital kommunikasjon",
          "namespace": "team-test",
          "appnavn": "demo-app"
        }
    """

    val alleHendelser = listOf(
        OPPRETTET,
        INAKTIVERT,
        SLETTET,
        BESTILT,
        VENTER,
        KANSELLERT,
        SMS_SENDT,
        SMS_FERDIGSTILT,
        FEILET,
    )
}
