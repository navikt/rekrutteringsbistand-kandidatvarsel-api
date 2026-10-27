package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.kandidatvarsel.SecureLog
import no.nav.toi.kandidatvarsel.VarselService
import no.nav.toi.kandidatvarsel.log
import no.nav.toi.kandidatvarsel.minside.CvDeltMedArbeidsgiver
import tools.jackson.databind.JsonNode
import java.nio.charset.StandardCharsets.UTF_8
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class KandidatCvDeltMedArbeidsgiverLytter(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource,
    private val clusterName: String = System.getenv("NAIS_CLUSTER_NAME") ?: "local",
) : River.PacketListener {
    private val secureLog = SecureLog(log)

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.requireKey(FØDSELSNUMMERLISTE_KEY)
            }
            validate {
                it.requireKey(
                    "kandidater",
                    "stillingsId",
                    "utførtAvNavIdent",
                    "tidspunkt",
                )
                it.require("stillingsId") { node -> UUID.fromString(node.asString()) }
                it.require("tidspunkt") { node -> ZonedDateTime.parse(node.asString()) }
                it.require("kandidater") { node ->
                    require(node.isObject) { "'kandidater' må være et objekt med aktørId som nøkler" }
                }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val stillingId = packet["stillingsId"].asString()
        val avsenderNavident = packet["utførtAvNavIdent"].asString()
        val tidspunkt = ZonedDateTime.parse(packet["tidspunkt"].asString()).toInstant()
        val aktørIder = packet["kandidater"].propertyNames().toList()

        log.info("Mottok $EVENT_NAME for stillingId=$stillingId <se secure log>")
        secureLog.info("Mottok $EVENT_NAME for stillingId=$stillingId, antallKandidater=${aktørIder.size}, avsenderNavident=$avsenderNavident")

        try {
            val fødselsnumre = hentOgValiderFødselsnumre(packet[FØDSELSNUMMERLISTE_KEY], aktørIder)
            fødselsnumre.forEach { (aktørId, fnr) ->
                val varselId = UUID.nameUUIDFromBytes(
                    "${CvDeltMedArbeidsgiver.name}:$stillingId:$aktørId:$tidspunkt".toByteArray(UTF_8),
                ).toString()
                VarselService.opprettVarsler(
                    dataSource = dataSource,
                    avsenderReferanseId = stillingId,
                    fnrList = listOf(fnr),
                    mal = CvDeltMedArbeidsgiver,
                    avsenderNavident = avsenderNavident,
                    varselId = varselId,
                )
            }
            log.info("Behandlet $EVENT_NAME for stillingId=$stillingId")
        } catch (e: Exception) {
            log.error("Feil ved behandling av $EVENT_NAME for stillingId=$stillingId")
            secureLog.error("Feil ved behandling av $EVENT_NAME for stillingId=$stillingId", e)
            throw e
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved parsing av $EVENT_NAME-melding: <se secure log>")
        secureLog.error("Feil ved parsing av $EVENT_NAME-melding: ${problems.toExtendedReport()}")
    }

    private companion object {
        const val EVENT_NAME = "kandidat_v2.DelCvMedArbeidsgiver"
        const val FØDSELSNUMMERLISTE_KEY = "fodselsnummerliste"
        const val DEV_GCP_CLUSTER = "dev-gcp"
        val FNR_REGEX = Regex("\\d{11}")
    }

    private fun hentOgValiderFødselsnumre(
        fødselsnummerlisteNode: JsonNode,
        aktørIder: List<String>
    ): List<Pair<String, String>> {
        require(fødselsnummerlisteNode.isObject) { "'$FØDSELSNUMMERLISTE_KEY' må være objekt" }

        return aktørIder.mapNotNull { aktørId ->
            val fnrNode = fødselsnummerlisteNode[aktørId]
                ?: throw IllegalArgumentException("Mangler fødselsnummer for kandidat")
            require(!fnrNode.isMissingNode) { "Mangler fødselsnummer for kandidat" }
            if (fnrNode.isNull) {
                if (clusterName == DEV_GCP_CLUSTER) {
                    log.warn("Mangler fødselsnummer for kandidat i $EVENT_NAME, hopper over kandidat i dev")
                    secureLog.warn("Mangler fødselsnummer for kandidat i $EVENT_NAME, aktørId=$aktørId")
                    return@mapNotNull null
                }
                throw IllegalArgumentException("Fødselsnummer kan ikke være null")
            }
            require(fnrNode.isString) { "Fødselsnummer må være tekstverdi" }
            val fnr = fnrNode.asString()
            require(fnr.matches(FNR_REGEX)) { "Ugyldig fødselsnummerformat" }
            aktørId to fnr
        }
    }
}
