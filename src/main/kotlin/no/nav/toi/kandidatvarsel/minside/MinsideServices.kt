package no.nav.toi.kandidatvarsel.minside

import no.nav.toi.kandidatvarsel.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import java.util.*
import javax.sql.DataSource

/** Finn neste varsel som er klar for bestilling, og bestill det hos minside.
 *
 * Returner false om det ikke var noe å gjøre. */
fun bestillVarsel(
    dataSource: DataSource,
    stillingClient: StillingClient,
    kafkaProducer: Producer<String, String>,
): Boolean = dataSource.transaction { tx ->
    val minsideVarsel = MinsideVarsel.finnOgLåsUsendtVarsel(tx) ?:
        return@transaction false
    
    val (tittel, arbeidsgiver) = when (minsideVarsel.mal.varselType) {
        VarselType.STILLING -> {
            val stilling = stillingClient.getStilling(UUID.fromString(minsideVarsel.avsenderReferanseId))
                ?: throw IllegalStateException("Kunne ikke hente stilling med id ${minsideVarsel.avsenderReferanseId}")
            Pair(stilling.title, stilling.businessName)
        }
        VarselType.REKRUTTERINGSTREFF -> {
            // Rekrutteringstreff-varslene trenger ikke tittel og arbeidsgiver
            Pair(null, null)
        }
    }
    
    kafkaProducer.sendBestilling(minsideVarsel, tittel, arbeidsgiver)
    val oppdatertVarsel = minsideVarsel.markerBestilt()
    oppdatertVarsel.save(tx)
    return@transaction true
}

/** Les siste oppdateringer fra minside. */
fun sjekkVarselOppdateringer(
    dataSource: DataSource,
    kafkaConsumer: Consumer<String, String>,
) {
    kafkaConsumer.pollOppdateringer { oppdateringer ->
        dataSource.transaction { tx ->
            for (oppdatering in oppdateringer) {
                val varsel = MinsideVarsel.finnFraVarselId(tx, oppdatering.varselId) ?: continue
                varsel.oppdaterFra(oppdatering).save(tx)
            }
        }
    }
}


