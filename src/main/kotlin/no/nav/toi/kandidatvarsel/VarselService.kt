package no.nav.toi.kandidatvarsel

import no.nav.toi.kandidatvarsel.minside.Mal
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel
import org.slf4j.LoggerFactory
import javax.sql.DataSource

object VarselService {
    private val log = LoggerFactory.getLogger(VarselService::class.java)

    fun opprettVarsler(
        dataSource: DataSource,
        varselId: String,
        fnrList: List<String>,
        mal: Mal,
        avsenderNavident: String,
    ) {
        log.info("Oppretter ${fnrList.size} varsler for varselId=$varselId med mal=${mal.name}")
        
        dataSource.transaction { tx ->
            for (fnr in fnrList) {
                MinsideVarsel.create(
                    mal = mal,
                    avsenderReferanseId = varselId,
                    mottakerFnr = fnr,
                    avsenderNavident = avsenderNavident,
                ).insert(tx)
            }
        }
        
        log.info("Opprettet ${fnrList.size} varsler for varselId=$varselId")
    }
}
