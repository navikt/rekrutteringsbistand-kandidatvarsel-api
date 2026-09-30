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
import no.nav.toi.kandidatvarsel.minside.DelCv
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class KandidatDelCvLytter(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource,
) : River.PacketListener {
    private val secureLog = SecureLog(log)

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.forbid("aktivitetskortuuid")
            }
            validate {
                it.requireKey(
                    "fnr",
                    "stillingsId",
                    "stillingsTittel",
                    "svarfrist",
                    "forespurtAvIdent",
                    "forespurtTidspunkt",
                )
                it.require("stillingsId") { node -> UUID.fromString(node.asString()) }
                it.require("svarfrist") { node -> ZonedDateTime.parse(node.asString()) }
                it.require("forespurtTidspunkt") { node -> ZonedDateTime.parse(node.asString()) }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fnr = packet["fnr"].asString()
        val stillingId = packet["stillingsId"].asString()
        val avsenderNavident = packet["forespurtAvIdent"].asString()

        log.info("Mottok $EVENT_NAME for stillingId=$stillingId <se secure log>")
        secureLog.info("Mottok $EVENT_NAME for stillingId=$stillingId, fnr=$fnr, avsenderNavident=$avsenderNavident")

        try {
            VarselService.opprettVarsler(
                dataSource = dataSource,
                avsenderReferanseId = stillingId,
                fnrList = listOf(fnr),
                mal = DelCv,
                avsenderNavident = avsenderNavident,
            )
            log.info("Behandlet $EVENT_NAME for stillingId=$stillingId")
        } catch (e: Exception) {
            log.error("Feil ved behandling av $EVENT_NAME for stillingId=$stillingId")
            secureLog.error("Feil ved behandling av $EVENT_NAME for stillingId=$stillingId, fnr=$fnr", e)
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
        const val EVENT_NAME = "samtykke-forespurt-om-deling-av-cv"
    }
}
