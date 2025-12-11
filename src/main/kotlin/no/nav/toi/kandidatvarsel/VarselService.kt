package no.nav.toi.kandidatvarsel

import no.nav.toi.kandidatvarsel.minside.Mal
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel
import org.slf4j.LoggerFactory
import javax.sql.DataSource

object VarselService {
    private val log = LoggerFactory.getLogger(VarselService::class.java)

    fun opprettVarsler(
        dataSource: DataSource,
        rekrutteringstreffId: String,
        fnrList: List<String>,
        mal: Mal,
        avsenderNavident: String,
        varselId: String? = null,
        /** Flettedata med endringsinformasjon som flettes inn i meldingstekster.
         *  Inneholder liste med displayTekster for endrede felter, f.eks. ["tidspunkt", "sted"] */
        flettedata: List<String>? = null
    ) {
        if (varselId != null && fnrList.size > 1) {
            throw IllegalArgumentException("Kan ikke opprette varsler med samme varselId for flere mottakere")
        }

        log.info("Oppretter ${fnrList.size} varsler for rekrutteringstreffId=$rekrutteringstreffId med mal=${mal.name}")
        
        dataSource.transaction { tx ->
            fnrList.filter {
                varselId == null || MinsideVarsel.finnFraVarselId(tx, varselId) == null
            }.forEach { fnr ->
                MinsideVarsel.create(
                    mal = mal,
                    avsenderReferanseId = rekrutteringstreffId,
                    mottakerFnr = fnr,
                    avsenderNavident = avsenderNavident,
                    varselId = varselId,
                    flettedata = flettedata
                ).insert(tx)
            }
        }
        
        log.info("Opprettet ${fnrList.size} varsler for rekrutteringstreffId=$rekrutteringstreffId")
    }
}
