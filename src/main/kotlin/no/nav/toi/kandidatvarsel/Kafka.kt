package no.nav.toi.kandidatvarsel

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer

class KafkaConfig(
    val bootstrapServers: String,
    val keystoreLocation: String,
    val keystorePassword: String,
    val truststoreLocation: String,
    val truststorePassword: String,
) {
    val asKafkaProperties: Map<String, String> get() = mapOf(
        "bootstrap.servers" to bootstrapServers,
        "security.protocol" to  "SSL",
        "ssl.keystore.type" to "PKCS12",
        "ssl.keystore.location" to keystoreLocation,
        "ssl.keystore.password" to keystorePassword,
        "ssl.truststore.type" to "JKS",
        "ssl.truststore.location" to truststoreLocation,
        "ssl.truststore.password" to truststorePassword,
    )

    companion object {
        fun nais() = KafkaConfig(
            bootstrapServers = System.getenv("KAFKA_BROKERS"),
            keystoreLocation = System.getenv("KAFKA_KEYSTORE_PATH"),
            keystorePassword = System.getenv("KAFKA_CREDSTORE_PASSWORD"),
            truststoreLocation = System.getenv("KAFKA_TRUSTSTORE_PATH"),
            truststorePassword = System.getenv("KAFKA_CREDSTORE_PASSWORD"),
        )
    }
}

fun KafkaConfig.minsideOppdateringsConsumer(): KafkaConsumer<String, String> {
    val config = this.asKafkaProperties + mapOf(
        "group.id" to "rekrutteringsbistand-kandidatvarsel-0"
    )
    return KafkaConsumer(config, StringDeserializer(), StringDeserializer())
}

fun KafkaConfig.minsideBestillingsProducer(): KafkaProducer<String, String> {
    val config = this.asKafkaProperties
    return KafkaProducer(config, StringSerializer(), StringSerializer())
}
