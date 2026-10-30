package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.kandidatvarsel.SecureLog
import no.nav.toi.kandidatvarsel.log
import tools.jackson.databind.JsonNode
import java.time.ZonedDateTime
import java.util.UUID

class KandidatCvDeltMedArbeidsgiverBehovLytter(
    private val rapidsConnection: RapidsConnection,
) : River.PacketListener {
    private val secureLog = SecureLog(log)

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.forbid(FØDSELSNUMMERLISTE_KEY)
                it.interestedIn("@behov", parser = { node ->
                    require(FØDSELSNUMMERLISTE_KEY !in node.toList().map(JsonNode::stringValue))
                })
            }
            validate {
                it.requireKey("kandidater", "stillingsId", "utførtAvNavIdent", "tidspunkt", "@id")
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
        try {
            packet["@behov"] = packet["@behov"].toList().map(JsonNode::stringValue) + FØDSELSNUMMERLISTE_KEY
            packet["aktøridliste"] = packet["kandidater"].propertyNames().toList()
            rapidsConnection.publish(packet.toJson())
            log.info("Publiserte behov for $EVENT_NAME")
        } catch (e: Exception) {
            log.error("Feil ved publisering av behov for $EVENT_NAME")
            secureLog.error("Feil ved publisering av behov for $EVENT_NAME", e)
            throw e
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved parsing av $EVENT_NAME-melding for behov: <se secure log>")
        secureLog.error("Feil ved parsing av $EVENT_NAME-melding for behov: ${problems.toExtendedReport()}")
    }

    private companion object {
        const val EVENT_NAME = "kandidat_v2.DelCvMedArbeidsgiver"
        const val FØDSELSNUMMERLISTE_KEY = "fodselsnummerliste"
    }
}
