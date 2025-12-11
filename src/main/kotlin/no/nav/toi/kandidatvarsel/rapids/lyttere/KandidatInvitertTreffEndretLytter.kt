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
                it.requireKey("rekrutteringstreffId", "fnr", "hendelseId", "endredeFelter")
                it.interestedIn("endretAv")
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
        
        // Leser endredeFelter fra rapid-meldingen og konverterer til flettedata for varsling
        val endredeFelterNode = packet["endredeFelter"]
        val flettedata: List<String> = if (endredeFelterNode.isArray && endredeFelterNode.size() > 0) {
            endredeFelterNode.mapNotNull { node ->
                try {
                    EndringFlettedata.valueOf(node.asText()).displayTekst  // Konverter til displayTekst for varsling
                } catch (e: IllegalArgumentException) {
                    log.warn("Ukjent endredeFelter-verdi: ${node.asText()}, ignorerer")
                    null
                }
            }
        } else {
            log.info("Ingen endredeFelter for rekrutteringstreffId=$rekrutteringstreffId, hopper over varsling")
            return
        }
        
        if (flettedata.isEmpty()) {
            log.info("endredeFelter inneholder kun ugyldige verdier for rekrutteringstreffId=$rekrutteringstreffId, hopper over varsling")
            return
        }

        log.info("Mottok rekrutteringstreffoppdatering-hendelse for rekrutteringstreffId=$rekrutteringstreffId med endredeFelter, konvertert til flettedata=$flettedata")
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
