package no.nav.toi.kandidatvarsel.minside

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.kandidatvarsel.StillingClient
import no.nav.toi.kandidatvarsel.transaction
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource

private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

/** Finn neste varsel som er klar for bestilling, og bestill det hos minside.
 *
 * Returner false om det ikke var noe å gjøre. */
fun bestillVarsel(
    dataSource: DataSource,
    stillingClient: StillingClient,
    kafkaProducer: Producer<String, String>,
): Boolean = dataSource.transaction { tx ->
    val minsideVarsel = MinsideVarsel.finnOgLåsUsendtVarsel(tx) ?: return@transaction false

    when (val mal = minsideVarsel.mal) {
        is StillingMal -> {
            val stilling = stillingClient.getStilling(UUID.fromString(minsideVarsel.avsenderReferanseId))
                ?: throw IllegalStateException("Kunne ikke hente stilling med id ${minsideVarsel.avsenderReferanseId}")
            kafkaProducer.sendBestilling(minsideVarsel, mal, stilling.title, stilling.businessName)
        }

        is RekrutteringstreffMal -> {
            kafkaProducer.sendBestilling(minsideVarsel, mal)
        }
    }

    val oppdatertVarsel = minsideVarsel.markerBestilt()
    oppdatertVarsel.save(tx)
    return@transaction true
}

/** Les siste oppdateringer fra minside. */
fun sjekkVarselOppdateringer(
    dataSource: DataSource,
    kafkaConsumer: Consumer<String, String>,
    rapidsConnection: RapidsConnection
) {
    val log = LoggerFactory.getLogger(Any::class.java)

    kafkaConsumer.pollOppdateringer { oppdateringerSeq ->
        val oppdateringer = oppdateringerSeq.toList()
        if (oppdateringer.isEmpty()) return@pollOppdateringer

        log.info("Behandler ${oppdateringer.size} oppdateringer")

        dataSource.transaction { tx ->
            val varsler = MinsideVarsel.finnFraVarselIder(tx, oppdateringer.map { it.varselId })
                .associateBy { it.varselId }
                .toMutableMap()

            oppdateringer.forEach { oppdatering ->
                val varsel = varsler[oppdatering.varselId]
                if (varsel != null) {

                    val oppdatertVarsel = varsel.oppdaterFra(oppdatering)
                    oppdatertVarsel.save(tx)
                    varsler[oppdatering.varselId] = oppdatertVarsel
                    log.info("Lagret oppdatering for varsel ${oppdatering.varselId}, status: ${oppdatertVarsel.eksternStatus}, partitionOffset: ${oppdatering.partitionOffset}")

                    if (oppdatertVarsel.mal.brukerRapid() && oppdatertVarsel.skalPubliseresPåRapid()) {
                        log.info("Publiserer varsel ${oppdatering.varselId} på rapid, status: ${oppdatertVarsel.eksternStatus}, partitionOffset: ${oppdatering.partitionOffset}")
                        publiserPåRapid(oppdatertVarsel, rapidsConnection)
                    }
                }
            }
        }
    }
}

private fun publiserPåRapid(varsel: MinsideVarsel, rapidsConnection: RapidsConnection) {

    val responseDto = varsel.toResponse()
    val opprettetZoned = responseDto.opprettet.atZone(java.time.ZoneId.of("Europe/Oslo"))
    val packet = mapOf(
        "@event_name" to "minsideVarselSvar",
        "varselId" to responseDto.id,
        "avsenderReferanseId" to responseDto.stillingId,
        "fnr" to responseDto.mottakerFnr,
        "eksternStatus" to responseDto.eksternStatus,
        "minsideStatus" to responseDto.minsideStatus,
        "opprettet" to opprettetZoned.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        "avsenderNavident" to responseDto.avsenderNavident,
        "eksternFeilmelding" to responseDto.eksternFeilmelding,
        "eksternKanal" to responseDto.eksternKanal,
        "mal" to varsel.mal.name
    )
    rapidsConnection.publish(responseDto.mottakerFnr, objectMapper.writeValueAsString(packet))

}


