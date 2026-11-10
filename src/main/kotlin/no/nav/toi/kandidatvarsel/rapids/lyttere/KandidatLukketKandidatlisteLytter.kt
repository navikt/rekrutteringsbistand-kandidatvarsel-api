package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.kandidatvarsel.VarselService
import no.nav.toi.kandidatvarsel.log
import no.nav.toi.kandidatvarsel.minside.StillingsannonseOppdatert
import tools.jackson.databind.JsonNode
import java.nio.charset.StandardCharsets.UTF_8
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class KandidatLukketKandidatlisteLytter(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource,
    private val clusterName: String = System.getenv("NAIS_CLUSTER_NAME") ?: "local",
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.requireKey("fodselsnummerliste", "aktørIdSamtykkeStatusListe")
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
            val stillingId = packet["stillingsId"].asString()
            val tidspunkt = ZonedDateTime.parse(packet["tidspunkt"].asString()).toInstant()
            val kandidater = packet["aktørIderFikkIkkeJobben"].toList().map(JsonNode::asString).distinct()
            if (kandidater.isEmpty()) return
            val mottakere = hentMottakereMedSamtykke(packet["aktørIdSamtykkeStatusListe"], kandidater)
            if (mottakere.isEmpty()) return

            val fødselsnumre = hentOgValiderFødselsnumre(packet["fodselsnummerliste"], mottakere)
            fødselsnumre.forEach { (aktørId, fnr) ->
                val varselId = UUID.nameUUIDFromBytes(
                    "${StillingsannonseOppdatert.name}:$stillingId:$aktørId:$tidspunkt".toByteArray(UTF_8),
                ).toString()
                VarselService.opprettVarsler(
                    dataSource = dataSource,
                    avsenderReferanseId = stillingId,
                    fnrList = listOf(fnr),
                    mal = StillingsannonseOppdatert,
                    avsenderNavident = packet["utførtAvNavIdent"].asString(),
                    varselId = varselId,
                )
            }
            log.info("Behandlet $EVENT_NAME for stillingId=$stillingId, antallMottakere=${fødselsnumre.size}")
        } catch (e: Exception) {
            log.error("Feil ved behandling av $EVENT_NAME")
            throw e
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("Ugyldig $EVENT_NAME-melding")
    }

    private fun hentMottakereMedSamtykke(node: JsonNode, kandidater: List<String>): List<String> {
        require(node.isArray) { "'aktørIdSamtykkeStatusListe' må være en liste" }
        val statuser = node.toList().associate { status ->
            val aktørId = status.path("aktørId")
            val samtykkeStatus = status.path("samtykkeStatus")
            require(aktørId.isString && aktørId.asString().matches(Regex("\\d{13}"))) {
                "Ugyldig aktør-ID i samtykkestatus"
            }
            require(samtykkeStatus.isString && samtykkeStatus.asString() in SAMTYKKESTATUSER) {
                "Ugyldig samtykkestatus"
            }
            aktørId.asString() to samtykkeStatus.asString()
        }
        require(statuser.size == node.size()) { "Duplikate aktør-ID-er i samtykkestatus" }
        require(kandidater.all { it in statuser }) { "Mangler samtykkestatus for kandidat" }
        return kandidater.filter { statuser.getValue(it) == "HAR_GYLDIG_SAMTYKKE" }
    }

    private fun hentOgValiderFødselsnumre(node: JsonNode, mottakere: List<String>): List<Pair<String, String>> {
        require(node.isObject) { "'fodselsnummerliste' må være et objekt" }
        return mottakere.mapNotNull { aktørId ->
            val fnrNode = node[aktørId]
            require(fnrNode != null && !fnrNode.isMissingNode) { "Mangler fødselsnummer for kandidat" }
            if (fnrNode.isNull && clusterName == "dev-gcp") {
                log.warn("Mangler fødselsnummer for kandidat i $EVENT_NAME, hopper over kandidat i dev")
                return@mapNotNull null
            }
            require(fnrNode.isString && fnrNode.asString().matches(Regex("\\d{11}"))) {
                "Ugyldig fødselsnummerformat"
            }
            aktørId to fnrNode.asString()
        }
    }

    private companion object {
        const val EVENT_NAME = "kandidat_v2.LukketKandidatliste"
        val SAMTYKKESTATUSER = setOf("IKKE_SPURT", "IKKE_SVART", "HAR_GYLDIG_SAMTYKKE", "HAR_IKKE_GYLDIG_SAMTYKKE")
    }
}
