package no.nav.toi.kandidatvarsel.minside

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.builder.VarselActionBuilder
import no.nav.toi.kandidatvarsel.SecureLog
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Dokumentasjon om minside-varsel: https://navikt.github.io/tms-dokumentasjon/varsler/produsere */

const val BESTILLING_TOPIC = "min-side.aapen-brukervarsel-v1"
const val OPPDATERING_TOPIC = "min-side.aapen-varsel-hendelse-v1"

private val secureLog = SecureLog(LoggerFactory.getLogger("no.nav.toi.kandidatvarsel.MinsideClient"))

fun Producer<String, String>.sendBestilling(minsideVarsel: MinsideVarsel, mal: StillingMal, tittel: String, arbeidsgiver: String) {
    val log = LoggerFactory.getLogger("no.nav.toi.kandidatvarsel.MinsideClient")!!
    val clusterName = System.getenv("NAIS_CLUSTER_NAME") ?: "local"
    val isProd = clusterName == "prod-gcp"
    
    val varselJson = VarselActionBuilder.opprett {
        type = Varseltype.Beskjed
        varselId = minsideVarsel.varselId
        ident = minsideVarsel.mottakerFnr
        tekster += Tekst(
            default = true,
            tekst = mal.minsideTekst(tittel, arbeidsgiver),
            spraakkode = "nb",
        )
        link = mal.lenkeurl(minsideVarsel.avsenderReferanseId, isProd)
        eksternVarsling = EksternVarslingBestilling(
            prefererteKanaler = listOf(EksternKanal.SMS),
            epostVarslingstittel = mal.epostTittel(),
            epostVarslingstekst = mal.epostHtmlBody(),
            smsVarslingstekst = mal.smsTekst(),
        )
        aktivFremTil = ZonedDateTime.now(ZoneId.of("Z")).plusWeeks(10)
        sensitivitet = Sensitivitet.Substantial
        produsent = Produsent(
            appnavn = System.getenv("NAIS_APP_NAME") ?: "kandidatvarsel-api",
            cluster = clusterName,
            namespace = System.getenv("NAIS_NAMESPACE") ?: "toi",
        )
    }

    val metadataFuture = send(ProducerRecord(BESTILLING_TOPIC, minsideVarsel.varselId, varselJson))

    // Important to wait for send to finish, as it's only then we know the Kafka-server
    // has acknowledged the record.
    metadataFuture.get()

    log.info("kafkameldig sendt. varselId/key: '{}' metadata: {}", minsideVarsel.varselId, metadataFuture.get())
}

/** Holder genererte tekster for et rekrutteringstreff-varsel */
private data class VarselTekster(
    val minsideTekst: String,
    val smsTekst: String,
    val epostHtmlBody: String
)

/** Genererer tekster for rekrutteringstreff-maler, håndterer parametriserte maler */
private fun genererTekster(minsideVarsel: MinsideVarsel, mal: RekrutteringstreffMal, log: org.slf4j.Logger): VarselTekster {
    return when (mal) {
        is KandidatInvitertTreffEndret -> {
            val endringsTekster = minsideVarsel.hentEndringsTekster()
            if (endringsTekster.isEmpty()) {
                log.error("KandidatInvitertTreffEndret varsel mangler data (endringsTekster), varselId=${minsideVarsel.varselId}, avsenderReferanseId=${minsideVarsel.avsenderReferanseId}")
                secureLog.error("KandidatInvitertTreffEndret varsel mangler data (endringsTekster), varselId=${minsideVarsel.varselId}, avsenderReferanseId=${minsideVarsel.avsenderReferanseId}, fnr=${minsideVarsel.mottakerFnr}")
                throw IllegalStateException("KandidatInvitertTreffEndret krever at data er satt med displayTekster for endringene")
            }
            val minsideTekst = mal.minsideTekst(endringsTekster)
            val smsTekst = mal.smsTekst(endringsTekster)
            val epostHtmlBody = mal.epostHtmlBody(endringsTekster)
            log.info("Genererte tekster for parametrisert varsel varselId=${minsideVarsel.varselId}")
            VarselTekster(
                minsideTekst = minsideTekst,
                smsTekst = smsTekst,
                epostHtmlBody = epostHtmlBody
            )
        }
        else -> {
            VarselTekster(
                minsideTekst = mal.minsideTekst(),
                smsTekst = mal.smsTekst(),
                epostHtmlBody = mal.epostHtmlBody()
            )
        }
    }
}

fun Producer<String, String>.sendBestilling(minsideVarsel: MinsideVarsel, mal: RekrutteringstreffMal) {
    val log = LoggerFactory.getLogger("no.nav.toi.kandidatvarsel.MinsideClient")!!
    val clusterName = System.getenv("NAIS_CLUSTER_NAME") ?: "local"
    val isProd = clusterName == "prod-gcp"
    
    val genererteTekster = genererTekster(minsideVarsel, mal, log)
    
    val varselJson = VarselActionBuilder.opprett {
        type = Varseltype.Beskjed
        varselId = minsideVarsel.varselId
        ident = minsideVarsel.mottakerFnr
        tekster += Tekst(
            default = true,
            tekst = genererteTekster.minsideTekst,
            spraakkode = "nb",
        )
        link = mal.lenkeurl(minsideVarsel.avsenderReferanseId, isProd)
        eksternVarsling = EksternVarslingBestilling(
            prefererteKanaler = listOf(EksternKanal.SMS),
            epostVarslingstittel = mal.epostTittel(),
            epostVarslingstekst = genererteTekster.epostHtmlBody,
            smsVarslingstekst = genererteTekster.smsTekst,
        )
        aktivFremTil = ZonedDateTime.now(ZoneId.of("Z")).plusWeeks(10)
        sensitivitet = Sensitivitet.Substantial
        produsent = Produsent(
            appnavn = System.getenv("NAIS_APP_NAME") ?: "kandidatvarsel-api",
            cluster = clusterName,
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
    val partitionOffset: String
}

data class StatusOppdatering(
    override val varselId: String,
    override val partitionOffset: String,
    val status: MinsideStatus,
) : VarselOppdatering

data class EksternVarselBestilt(
    override val varselId: String,
    override val partitionOffset: String,
): VarselOppdatering

data class EksternVarselVenter(
    override val varselId: String,
    override val partitionOffset: String,
): VarselOppdatering

data class EksternVarselKansellert(
    override val varselId: String,
    override val partitionOffset: String,
): VarselOppdatering

data class EksternVarselFerdigstilt(
    override val varselId: String,
    override val partitionOffset: String,
): VarselOppdatering

data class EksternVarselSendt(
    override val varselId: String,
    override val partitionOffset: String,
    val kanal: Kanal,
): VarselOppdatering

data class EksternVarselFeilet(
    override val varselId: String,
    override val partitionOffset: String,
    val feilmelding: String,
): VarselOppdatering

private val minsideObjectMapper = jacksonObjectMapper()

fun Consumer<String, String>.pollOppdateringer(body: (Sequence<VarselOppdatering>) -> Unit) {
    val records = poll(1.seconds.toJavaDuration())

    val oppdateringer = records.asSequence()
        .filter { it.value() != null }
        .map { record -> 
            val partitionOffset = "${record.partition()}:${record.offset()}"
            val dto = minsideObjectMapper.readValue<VarselOppdateringDto>(record.value())
            dto.asDomain(partitionOffset) 
        }

    body(oppdateringer)

    commitSync()
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
    fun asDomain(partitionOffset: String): VarselOppdatering = when (eventName) {
        "opprettet" -> StatusOppdatering(varselId, partitionOffset, MinsideStatus.OPPRETTET)
        "inaktivert" -> StatusOppdatering(varselId, partitionOffset, MinsideStatus.INAKTIVERT)
        "slettet" -> StatusOppdatering(varselId, partitionOffset, MinsideStatus.SLETTET)
        "eksternStatusOppdatert" -> when (status) {
            "bestilt" -> EksternVarselBestilt(varselId, partitionOffset)
            "sendt" -> EksternVarselSendt(varselId, partitionOffset, Kanal.valueOf(kanal!!))
            "feilet" -> EksternVarselFeilet(varselId, partitionOffset, feilmelding!!)
            "venter" ->  EksternVarselVenter(varselId, partitionOffset)
            "kansellert" ->  EksternVarselKansellert(varselId, partitionOffset)
            "ferdigstilt" -> EksternVarselFerdigstilt(varselId, partitionOffset)
            else -> {
                secureLog.error("Ukjent status: $status i eksternStatusOppdatert for varselId: $varselId")
                throw IllegalStateException("Ukjent status: $status i eksternStatusOppdatert")
            }
        }
        else -> throw IllegalStateException("Ukjent @event_type '$eventName'")
    }
}
