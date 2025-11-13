package no.nav.toi.kandidatvarsel

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.toi.kandidatvarsel.minside.OPPDATERING_TOPIC
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringSerializer

class FakeMinside {
    val consumer = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)
    val producer = MockProducer(
        true,
        null,
        StringSerializer(),
        StringSerializer()
    )
    private val objectMapper = jacksonObjectMapper()
    private var producerHistoryOffset = 0
    private var consumerOffset = 0L

    init {
        val topicPartition = TopicPartition(OPPDATERING_TOPIC, 0)
        consumer.assign(listOf(topicPartition))
        consumer.updateBeginningOffsets(mapOf(topicPartition to 0))
    }

    fun mottatteBestillinger(n: Int = 1): Map<String, JsonNode> {
        val records = producer.history().subList(producerHistoryOffset, producerHistoryOffset + n)
        producerHistoryOffset += n
        return records.associate { record ->
            val json = objectMapper.readValue<JsonNode>(record.value())
            json["ident"].asText() to json
        }
    }

    fun varselOpprettet(varselId: String) = addRecord(varselId, "opprettet")
    fun varselInaktivert(varselId: String) = addRecord(varselId, "inaktivert")
    fun varselSlettet(varselId: String) = addRecord(varselId, "slettet")

    fun eksterntVarselBestilt(varselId: String) = addRecord(
        varselId,
        "eksternStatusOppdatert",
        mapOf(
            "status" to "bestilt"
        )
    )

    fun eksterntVarselSendt(varselId: String, kanal: String) = addRecord(
        varselId = varselId,
        eventName = "eksternStatusOppdatert",
        mapOf(
            "status" to "sendt",
            "kanal" to kanal,
            "renotifikasjon" to false
        )
    )

    fun eksterntVarselFeilet(varselId: String, feilmelding: String) = addRecord(
        varselId = varselId,
        eventName = "eksternStatusOppdatert",
        mapOf(
            "status" to "feilet",
            "feilmelding" to feilmelding
        )
    )


    private fun addRecord(varselId: String, eventName: String, hendelse: Map<String, Any> = emptyMap()) {
        consumer.addRecord(
            ConsumerRecord(
                OPPDATERING_TOPIC,
                0,
                consumerOffset++,
                varselId,
                objectMapper.writeValueAsString(
                    hendelse + mapOf(
                        "@event_name" to eventName,
                        "varselId" to varselId,
                        "varseltype" to "beskjed"
                    )
                ),
            )
        )
    }
}