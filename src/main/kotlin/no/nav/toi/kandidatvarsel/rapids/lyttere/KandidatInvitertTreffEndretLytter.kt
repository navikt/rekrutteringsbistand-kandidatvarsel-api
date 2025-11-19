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
                it.requireValue("@event_name", "kandidatInvitertTreffEndret")
            }
            validate {
                it.requireKey("varselId", "fnr", "avsenderNavident")
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
        val fnr = packet["fnr"].asText()
        val avsenderNavident = packet["avsenderNavident"].asText()

        log.info("Mottok kandidatInvitertTreffEndret-hendelse for varselId=$varselId")
        secureLog.info("Mottok kandidatInvitertTreffEndret-hendelse for varselId=$varselId, fnr=$fnr, avsenderNavident=$avsenderNavident")

        try {
            VarselService.opprettVarsler(
                dataSource = dataSource,
                varselId = varselId,
                fnrList = listOf(fnr),
                mal = KandidatInvitertTreffEndret,
                avsenderNavident = avsenderNavident
            )
            log.info("Behandlet kandidatInvitertTreffEndret-hendelse for varselId=$varselId")
        } catch (e: Exception) {
            log.error("Feil ved behandling av kandidatInvitertTreffEndret-hendelse for varselId=$varselId", e)
            secureLog.error("Feil ved behandling av kandidatInvitertTreffEndret-hendelse for varselId=$varselId, fnr=$fnr", e)
            throw e
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("Feil ved parsing av kandidatInvitertTreffEndret-melding: <se secure log>")
        secureLog.error("Feil ved parsing av kandidatInvitertTreffEndret-melding: ${problems.toExtendedReport()}")
    }
}
