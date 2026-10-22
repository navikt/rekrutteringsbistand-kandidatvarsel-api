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
import no.nav.toi.kandidatvarsel.minside.CvDeltMedArbeidsgiver
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class KandidatCvDeltMedArbeidsgiverLytter(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource,
) : River.PacketListener {
    private val secureLog = SecureLog(log)

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.requireKey("aktivitetskortuuid")
            }
            validate {
                it.requireKey(
                    "fnr",
                    "stillingsId",
                    "utførtAvNavIdent",
                    "tidspunkt",
                    "@id",
                )
                it.require("stillingsId") { node -> UUID.fromString(node.asString()) }
                it.require("tidspunkt") { node -> ZonedDateTime.parse(node.asString()) }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val stillingId = packet["stillingsId"].asString()
        val avsenderNavident = packet["utførtAvNavIdent"].asString()
        val hendelseId = packet["@id"].asString()

        val fødselsnumre = packet["kandidater"].asArray().flatMap { it.propertyNames() }
        log.info("Mottok $EVENT_NAME for stillingId=$stillingId <se secure log>")
        secureLog.info("Mottok $EVENT_NAME for stillingId=$stillingId, fnr=$fødselsnumre, avsenderNavident=$avsenderNavident")

        try {
            VarselService.opprettVarsler(
                dataSource = dataSource,
                avsenderReferanseId = stillingId,
                fnrList = fødselsnumre,
                mal = CvDeltMedArbeidsgiver,
                avsenderNavident = avsenderNavident,
                varselId = hendelseId,
            )
            log.info("Behandlet $EVENT_NAME for stillingId=$stillingId")
        } catch (e: Exception) {
            log.error("Feil ved behandling av $EVENT_NAME for stillingId=$stillingId")
            secureLog.error("Feil ved behandling av $EVENT_NAME for stillingId=$stillingId, fnr=$fødselsnumre", e)
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
        const val EVENT_NAME = "kandidat_v2.DelCvMedArbeidsgiver"
    }
}
