package no.nav.toi.kandidatvarsel.altinnsms

import no.nav.toi.kandidatvarsel.*
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.LocalDateTime

enum class AltinnStatus {
    SENDT, UNDER_UTSENDING, IKKE_SENDT, FEIL
}

/** Gammel løsning sendte SMS via altinn. Det blir ikke
 * opprettet nye, men vi har historikk på stillinger og
 * kandidater. */
data class AltinnVarsel(
    private val avsenderNavident: String,
    private val mottakerFnr: String,
    private val frontendId: String,
    private val opprettet: LocalDateTime,
    private val stillingId: String,
    private val status: AltinnStatus,
) {
    fun toResponse() = VarselResponseDto(
        id = frontendId,
        opprettet = opprettet,
        stillingId = stillingId,
        mottakerFnr = mottakerFnr,
        avsenderNavident = avsenderNavident,
        minsideStatus = MinsideStatusDto.IKKE_BESTILT,
        eksternStatus = when (status) {
            AltinnStatus.IKKE_SENDT -> EksternStatusDto.UNDER_UTSENDING
            AltinnStatus.UNDER_UTSENDING -> EksternStatusDto.UNDER_UTSENDING
            AltinnStatus.SENDT -> EksternStatusDto.VELLYKKET_SMS
            AltinnStatus.FEIL -> EksternStatusDto.FEIL
        },
        eksternFeilmelding = null,
    )


    companion object {
        object RowMapper : org.springframework.jdbc.core.RowMapper<AltinnVarsel> {
            override fun mapRow(rs: ResultSet, rowNum: Int) = AltinnVarsel(
                avsenderNavident = rs.getString("avsender_navident"),
                frontendId = rs.getString("frontend_id"),
                opprettet = rs.getTimestamp("opprettet").toLocalDateTime(),
                stillingId = rs.getString("stilling_id"),
                mottakerFnr = rs.getString("mottaker_fnr"),
                status = AltinnStatus.valueOf(rs.getString("status")),
            )
        }

        fun hentVarslerForStilling(jdbcClient: JdbcClient, stillingId: String): List<AltinnVarsel> =
            jdbcClient.sql("""select * from altinn_varsel where stilling_id = :stilling_id""")
                .param("stilling_id", stillingId)
                .query(RowMapper)
                .list()

        fun hentVarslerForQuery(jdbcClient: JdbcClient, queryRequestDto: QueryRequestDto): List<AltinnVarsel> =
            jdbcClient.sql("""select * from altinn_varsel where mottaker_fnr = :mottaker_fnr""")
                .param("mottaker_fnr", queryRequestDto.fnr)
                .query(RowMapper)
                .list()

        fun storeBackfill(jdbcClient: JdbcClient, backfillRequest: BackfillRequest) =
            jdbcClient.sql("""
                insert into altinn_varsel(
                    frontend_id,
                    opprettet,
                    stilling_id,
                    melding,
                    mottaker_fnr,
                    status,
                    status_endret,
                    avsender_navident
                )
                values (
                    :frontend_id,
                    :opprettet,
                    :stilling_id,
                    :melding,
                    :mottaker_fnr,
                    :status,
                    :status_endret,
                    :avsender_navident
                )
                on conflict (frontend_id) do update set
                    status = excluded.status,
                    status_endret = excluded.status_endret
            """.trimIndent())
                .param("frontend_id", backfillRequest.frontendId)
                .param("opprettet", backfillRequest.opprettet)
                .param("stilling_id", backfillRequest.stillingId)
                .param("melding", backfillRequest.melding)
                .param("mottaker_fnr", backfillRequest.fnr)
                .param("avsender_navident", backfillRequest.navIdent)
                .param("status", backfillRequest.status)
                .param("status_endret", backfillRequest.statusEndret)
                .update()
    }
}
