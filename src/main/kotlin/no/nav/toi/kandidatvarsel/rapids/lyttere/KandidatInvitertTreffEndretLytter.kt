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
import no.nav.toi.kandidatvarsel.minside.*
import javax.sql.DataSource

class KandidatInvitertTreffEndretLytter(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource,
    private val eventName: String = "rekrutteringstreffoppdatering",
    private val mal: Mal = KandidatInvitertTreffEndret
) : River.PacketListener {
    private val secureLog = SecureLog(log)

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", eventName)
                // Oppdateringer uten endredeFelter gjelder kun aktivitetskortet og skal ikke varsles
                it.requireKey("endredeFelter")
            }
            validate {
                it.requireKey("rekrutteringstreffId", "fnr", "hendelseId")
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
        val rekrutteringstreffId = packet["rekrutteringstreffId"].asString()
        val fnr = packet["fnr"].asString()
        val avsenderNavident = packet["endretAv"].asString("SYSTEM")
        val hendelseId = packet["hendelseId"].asString()

        // Leser endredeFelter fra rapid-meldingen og konverterer til flettedata for varsling
        val endredeFelterNode = packet["endredeFelter"]
        val flettedata: List<String> = if (endredeFelterNode.isArray && endredeFelterNode.size() > 0) {
            endredeFelterNode.mapNotNull { node ->
                if (!node.isString) {
                    log.warn("Ignorerer endredeFelter-element som ikke er tekst, type=${node.nodeType}")
                    return@mapNotNull null
                }
                try {
                    EndringFlettedata.valueOf(node.asString()).displayTekst  // Konverter til displayTekst for varsling
                } catch (e: IllegalArgumentException) {
                    log.warn("Ukjent endredeFelter-verdi: ${node.asString()}, ignorerer", e)
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

        log.info("Mottok $eventName-hendelse for rekrutteringstreffId=$rekrutteringstreffId med endredeFelter, konvertert til flettedata=$flettedata, mal=${mal.name}")
        secureLog.info("Mottok $eventName-hendelse for rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr, avsenderNavident=$avsenderNavident, hendelseId=$hendelseId, flettedata=$flettedata, mal=${mal.name}")

        try {
            VarselService.opprettVarsler(
                dataSource = dataSource,
                avsenderReferanseId = rekrutteringstreffId,
                fnrList = listOf(fnr),
                mal = mal,
                avsenderNavident = avsenderNavident,
                varselId = hendelseId,
                flettedata = flettedata
            )
            log.info("Behandlet $eventName-hendelse for rekrutteringstreffId=$rekrutteringstreffId")
        } catch (e: Exception) {
            log.error("Feil ved behandling av $eventName-hendelse for rekrutteringstreffId=$rekrutteringstreffId", e)
            secureLog.error("Feil ved behandling av $eventName-hendelse for rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr", e)
            throw e
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("Feil ved parsing av $eventName-melding: <se secure log>")
        secureLog.error("Feil ved parsing av $eventName-melding: ${problems.toExtendedReport()}")
    }
}
