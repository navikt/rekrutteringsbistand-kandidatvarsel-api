package no.nav.toi.kandidatvarsel.minside

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.builder.VarselActionBuilder
import no.nav.toi.kandidatvarsel.Stilling
import no.nav.toi.kandidatvarsel.log
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Dokumentasjon om minside-varsel: https://navikt.github.io/tms-dokumentasjon/varsler/produsere */

const val BESTILLING_TOPIC = "min-side.aapen-brukervarsel-v1"
const val OPPDATERING_TOPIC = "min-side.aapen-varsel-hendelse-v1"

fun Producer<String, String>.sendBestilling(minsideVarsel: MinsideVarsel, stilling: Stilling) {
    val varselJson = VarselActionBuilder.opprett {
        type = Varseltype.Beskjed
        varselId = minsideVarsel.varselId
        ident = minsideVarsel.mottakerFnr
        tekster += Tekst(
            default = true,
            tekst = minsideVarsel.mal.minsideTekst(stilling),
            spraakkode = "nb",
        )
        link = "https://www.nav.no/arbeid/stilling/${minsideVarsel.stillingId}"
        eksternVarsling = EksternVarslingBestilling(
            prefererteKanaler = listOf(EksternKanal.SMS),
            epostVarslingstittel = minsideVarsel.mal.epostTittel(),
            epostVarslingstekst = minsideVarsel.mal.epostHtmlBody(),
            smsVarslingstekst = minsideVarsel.mal.smsTekst(),
        )
        aktivFremTil = ZonedDateTime.now(ZoneId.of("Z")).plusWeeks(10)
        sensitivitet = Sensitivitet.Substantial
        produsent = Produsent(
            appnavn = System.getenv("NAIS_APP_NAME") ?: "kandidatvarsel-api",
            cluster = System.getenv("NAIS_CLUSTER_NAME") ?: "local",
            namespace = System.getenv("NAIS_NAMESPACE") ?: "toi",
        )
    }

    val metadataFuture = send(ProducerRecord(BESTILLING_TOPIC, minsideVarsel.varselId, varselJson))

    // Important to wait for send to finish, as it's only then we know the Kafka-server
    // has acknowledged the record.
    metadataFuture.get()

    log.info("kafkameldig sendt. varselId/key: '{}' metadata: {}", minsideVarsel.varselId, metadataFuture.get())
}


sealed interface VarselOppdatering {
    val varselId: String
}

data class StatusOppdatering(
    override val varselId: String,
    val status: MinsideStatus,
) : VarselOppdatering

data class EksternVarselBestilt(
    override val varselId: String,
): VarselOppdatering

data class EksternVarselSendt(
    override val varselId: String,
    val kanal: Kanal,
): VarselOppdatering

data class EksternVarselFeilet(
    override val varselId: String,
    val feilmelding: String,
): VarselOppdatering

private val minsideObjectMapper = jacksonObjectMapper()

fun Consumer<String, String>.pollOppdateringer(body: (Sequence<VarselOppdatering>) -> Unit) {
    val records = poll(1.seconds.toJavaDuration())

    val oppdateringer = records.asSequence()
        .map {  it.value() }
        .filterNotNull()
        .map { minsideObjectMapper.readValue<VarselOppdateringDto>(it).asDomain() }

    body(oppdateringer)

    commitAsync()
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class VarselOppdateringDto(
    @JsonProperty("@event_name")
    val eventName: String,
    val varselId: String,
    val status: String? = null,
    val kanal: String? = null,
    val feilmelding: String? = null,
) {
    fun asDomain(): VarselOppdatering = when (eventName) {
        "opprettet" -> StatusOppdatering(varselId, MinsideStatus.OPPRETTET)
        "inaktivert" -> StatusOppdatering(varselId, MinsideStatus.INAKTIVERT)
        "slettet" -> StatusOppdatering(varselId, MinsideStatus.SLETTET)
        "eksternStatusOppdatert" -> when (status) {
            "bestilt" -> EksternVarselBestilt(varselId)
            "sendt" -> EksternVarselSendt(varselId, Kanal.valueOf(kanal!!))
            "feilet" -> EksternVarselFeilet(varselId, feilmelding!!)
            else -> throw IllegalStateException("Ukjent status i eksternStatusOppdatert")
        }
        else -> throw IllegalStateException("Ukjent @event_type '$eventName'")
    }
}
