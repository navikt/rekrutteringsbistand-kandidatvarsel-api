package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.kandidatvarsel.VarselService
import no.nav.toi.kandidatvarsel.minside.Mal
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class KandidatInvitertLytter(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource
) : River.PacketListener {

    private val log = LoggerFactory.getLogger(KandidatInvitertLytter::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "kandidat.invitert")
            }
            validate {
                it.requireKey("varselId", "fnr")
                it.interestedIn("avsenderNavident")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val varselId = packet["varselId"].asText()
        val fnrNode = packet["fnr"]
        val avsenderNavident = packet["avsenderNavident"].takeIf { !it.isMissingNode }?.asText() ?: "SYSTEM"
        
        val fnrList = if (fnrNode.isArray) {
            fnrNode.map { it.asText() }
        } else {
            listOf(fnrNode.asText())
        }

        log.info("Mottok kandidat.invitert-hendelse for varselId=$varselId med ${fnrList.size} kandidater")
        secureLog.info("Mottok kandidat.invitert-hendelse for varselId=$varselId, fnr=$fnrList, avsenderNavident=$avsenderNavident")

        try {
            VarselService.opprettVarsler(
                dataSource = dataSource,
                varselId = varselId,
                fnrList = fnrList,
                mal = Mal.Companion.KandidatInvitertTreff,
                avsenderNavident = avsenderNavident
            )
            log.info("Behandlet kandidat.invitert-hendelse for varselId=$varselId")
        } catch (e: Exception) {
            log.error("Feil ved behandling av kandidat.invitert-hendelse for varselId=$varselId", e)
            secureLog.error("Feil ved behandling av kandidat.invitert-hendelse for varselId=$varselId, fnr=$fnrList", e)
            throw e
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("Feil ved parsing av kandidat.invitert-melding: ${problems.toExtendedReport()}")
        secureLog.error("Feil ved parsing av kandidat.invitert-melding: ${problems.toExtendedReport()}")
    }
}
