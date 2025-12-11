package no.nav.toi.kandidatvarsel.rapids.lyttere

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.kandidatvarsel.VarselService
import no.nav.toi.kandidatvarsel.minside.*
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class KandidatInvitertTreffEndretLytter(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource
) : River.PacketListener {

    private val log = LoggerFactory.getLogger(KandidatInvitertTreffEndretLytter::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "rekrutteringstreffoppdatering")
            }
            validate {
                it.requireKey("rekrutteringstreffId", "fnr", "hendelseId")
                it.interestedIn("endretAv", "flettedata")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val rekrutteringstreffId = packet["rekrutteringstreffId"].asText()
        val fnr = packet["fnr"].asText()
        val avsenderNavident = packet["endretAv"].asText("SYSTEM")
        val hendelseId = packet["hendelseId"].asText()
        
        val flettedataNode = packet["flettedata"]
        val flettedata: List<String> = if (flettedataNode.isArray && flettedataNode.size() > 0) {
            flettedataNode.map { it.asText() }.filter { it.isNotBlank() }
        } else {
            log.error("Mangler eller tom flettedata-liste for rekrutteringstreffId=$rekrutteringstreffId, hopper over varsling")
            secureLog.error("Mangler eller tom flettedata-liste for rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr, hendelseId=$hendelseId")
            return
        }
        
        if (flettedata.isEmpty()) {
            log.error("Flettedata-liste inneholder kun tomme elementer for rekrutteringstreffId=$rekrutteringstreffId, hopper over varsling")
            secureLog.error("Flettedata-liste inneholder kun tomme elementer for rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr, hendelseId=$hendelseId")
            return
        }

        log.info("Mottok rekrutteringstreffoppdatering-hendelse for rekrutteringstreffId=$rekrutteringstreffId med flettedata=$flettedata")
        secureLog.info("Mottok rekrutteringstreffoppdatering-hendelse for rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr, avsenderNavident=$avsenderNavident, hendelseId=$hendelseId, flettedata=$flettedata")

        try {
            VarselService.opprettVarsler(
                dataSource = dataSource,
                rekrutteringstreffId = rekrutteringstreffId,
                fnrList = listOf(fnr),
                mal = KandidatInvitertTreffEndret,
                avsenderNavident = avsenderNavident,
                varselId = hendelseId,
                flettedata = flettedata
            )
            log.info("Behandlet rekrutteringstreffoppdatering-hendelse for rekrutteringstreffId=$rekrutteringstreffId")
        } catch (e: Exception) {
            log.error("Feil ved behandling av rekrutteringstreffoppdatering-hendelse for rekrutteringstreffId=$rekrutteringstreffId", e)
            secureLog.error("Feil ved behandling av rekrutteringstreffoppdatering-hendelse for rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr", e)
            throw e
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("Feil ved parsing av rekrutteringstreffoppdatering-melding: <se secure log>")
        secureLog.error("Feil ved parsing av rekrutteringstreffoppdatering-melding: ${problems.toExtendedReport()}")
    }
}
