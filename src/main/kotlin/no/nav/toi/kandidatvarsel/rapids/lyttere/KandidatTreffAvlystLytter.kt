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

class KandidatTreffAvlystLytter(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource
) : River.PacketListener {

    private val log = LoggerFactory.getLogger(KandidatTreffAvlystLytter::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "rekrutteringstreffSvarOgStatus")
                it.requireValue("svar", true)
                it.requireValue("treffstatus", "avlyst")
            }
            validate {
                it.requireKey("rekrutteringstreffId", "fnr")
                it.interestedIn("hendelseId")
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
        val hendelseId = packet["hendelseId"].asText().takeIf { it.isNotEmpty() } ?: packet.id

        log.info("Mottok rekrutteringstreffSvarOgStatus med avlysning for rekrutteringstreffId=$rekrutteringstreffId")
        secureLog.info("Mottok rekrutteringstreffSvarOgStatus med avlysning for rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr, hendelseId=$hendelseId")

        try {
            VarselService.opprettVarsler(
                dataSource = dataSource,
                rekrutteringstreffId = rekrutteringstreffId,
                fnrList = listOf(fnr),
                mal = KandidatInvitertTreffAvlyst,
                avsenderNavident = "SYSTEM",
                varselId = hendelseId
            )
            log.info("Behandlet avlysningsvarsel for rekrutteringstreffId=$rekrutteringstreffId")
        } catch (e: Exception) {
            log.error("Feil ved behandling av avlysningsvarsel for rekrutteringstreffId=$rekrutteringstreffId", e)
            secureLog.error("Feil ved behandling av avlysningsvarsel for rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr", e)
            throw e
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("Feil ved parsing av rekrutteringstreffSvarOgStatus-melding for avlysning: <se secure log>")
        secureLog.error("Feil ved parsing av rekrutteringstreffSvarOgStatus-melding for avlysning: ${problems.toExtendedReport()}")
    }
}
