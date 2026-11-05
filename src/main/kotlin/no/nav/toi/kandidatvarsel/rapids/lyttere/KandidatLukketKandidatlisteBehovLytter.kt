package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.kandidatvarsel.log
import tools.jackson.databind.JsonNode
import java.time.ZonedDateTime
import java.util.UUID

class KandidatLukketKandidatlisteBehovLytter(
    private val rapidsConnection: RapidsConnection,
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.forbid("fodselsnummerliste")
                it.interestedIn("@behov", parser = { node ->
                    require("fodselsnummerliste" !in node.toList().map(JsonNode::stringValue))
                })
            }
            validate {
                it.requireKey("stillingsId", "utførtAvNavIdent", "tidspunkt")
                it.require("stillingsId") { node -> UUID.fromString(node.asString()) }
                it.require("tidspunkt") { node -> ZonedDateTime.parse(node.asString()) }
                it.require("utførtAvNavIdent") { node ->
                    require(node.isString && node.asString().isNotBlank()) { "Mangler avsender" }
                }
                it.require("aktørIderFikkIkkeJobben") { node ->
                    require(node.isArray && node.all { id -> id.isString && id.asString().matches(Regex("\\d{13}")) }) {
                        "Kandidatlisten må være en liste med aktør-ID-er"
                    }
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
            val mottakere = packet["aktørIderFikkIkkeJobben"].toList().map(JsonNode::asString).distinct()
            if (mottakere.isEmpty()) return

            packet["@behov"] = packet["@behov"].toList().map(JsonNode::stringValue) + "fodselsnummerliste"
            packet["aktøridliste"] = mottakere
            rapidsConnection.publish(packet["stillingsId"].asString(), packet.toJson())
            log.info("Publiserte fødselsnummerbehov for $EVENT_NAME, antallMottakere=${mottakere.size}")
        } catch (e: Exception) {
            log.error("Feil ved behandling av fødselsnummerbehov for $EVENT_NAME")
            throw e
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("Ugyldig $EVENT_NAME-melding for fødselsnummerbehov")
    }

    private companion object {
        const val EVENT_NAME = "kandidat_v2.LukketKandidatliste"
    }
}
