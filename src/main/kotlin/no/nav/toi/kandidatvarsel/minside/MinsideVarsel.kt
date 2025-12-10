package no.nav.toi.kandidatvarsel.minside

import com.fasterxml.uuid.Generators
import no.nav.toi.kandidatvarsel.EksternStatusDto
import no.nav.toi.kandidatvarsel.MinsideStatusDto
import no.nav.toi.kandidatvarsel.QueryRequestDto
import no.nav.toi.kandidatvarsel.VarselResponseDto
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.jvm.optionals.getOrNull

enum class MinsideStatus {
    OPPRETTET, INAKTIVERT, SLETTET
}

enum class EksternStatus {
    BESTILLT, SENDT, FEILET, VENTER, KANSELLERT, FERDIGSTILT
}

enum class Kanal {
    SMS, EPOST
}


data class MinsideVarsel(
    private val dbid: Long?,
    val mal: Mal,
    val varselId: String,
    val avsenderReferanseId: String,
    val opprettet: LocalDateTime,
    val mottakerFnr: String,
    val avsenderNavIdent: String,
    val bestilt: Boolean,
    val minsideStatus: MinsideStatus?,
    val eksternStatus: EksternStatus?,
    val eksternKanal: Kanal?,
    val eksternFeilmelding: String?,
    val malParametere: List<MalParameter>? = null,
) {
    fun oppdaterFra(oppdatering: VarselOppdatering): MinsideVarsel = when (oppdatering) {
        is EksternVarselBestilt -> copy(eksternStatus = EksternStatus.BESTILLT)
        is EksternVarselFeilet -> copy(eksternStatus = EksternStatus.FEILET, eksternFeilmelding = oppdatering.feilmelding)
        is EksternVarselSendt -> copy(eksternStatus = EksternStatus.SENDT, eksternKanal = oppdatering.kanal)
        is EksternVarselVenter -> copy(eksternStatus = EksternStatus.VENTER)
        is EksternVarselKansellert -> copy(eksternStatus = EksternStatus.KANSELLERT)
        is EksternVarselFerdigstilt -> copy(eksternStatus = EksternStatus.FERDIGSTILT)
        is StatusOppdatering -> copy(minsideStatus = oppdatering.status)
    }

    fun markerBestilt() = copy(bestilt = true)

    /** Sjekker om varselet har en endelig ekstern status som skal publiseres på rapid */
    fun skalPubliseresPåRapid(): Boolean = 
        eksternStatus == EksternStatus.FERDIGSTILT || eksternStatus == EksternStatus.FEILET

    /** Serialiserer mal-navnet med parametere for lagring i databasen.
     * Format: "KANDIDAT_INVITERT_TREFF_ENDRET:NAVN,STED" */
    fun malMedParametere(): String {
        return if (mal == KandidatInvitertTreffEndret && malParametere != null && malParametere.isNotEmpty()) {
            "${mal.name}:${malParametere.joinToString(",") { it.name }}"
        } else {
            mal.name
        }
    }

    fun toResponse() = VarselResponseDto(
        /* De gamle Altinn-varslene brukte dbid som id.
         * Prefixer med "A" for å starte et nytt "namespace". */
        id = "A$dbid",
        opprettet = opprettet,
        stillingId = avsenderReferanseId,
        mottakerFnr = mottakerFnr,
        avsenderNavident = avsenderNavIdent,
        minsideStatus = when (minsideStatus) {
            null -> MinsideStatusDto.UNDER_UTSENDING
            MinsideStatus.OPPRETTET -> MinsideStatusDto.OPPRETTET
            MinsideStatus.INAKTIVERT -> MinsideStatusDto.OPPRETTET
            MinsideStatus.SLETTET -> MinsideStatusDto.SLETTET
        },
        eksternStatus = when (eksternStatus) {
            null -> EksternStatusDto.UNDER_UTSENDING
            EksternStatus.BESTILLT -> EksternStatusDto.UNDER_UTSENDING
            EksternStatus.VENTER -> EksternStatusDto.UNDER_UTSENDING
            EksternStatus.KANSELLERT -> EksternStatusDto.UNDER_UTSENDING
            EksternStatus.FERDIGSTILT -> EksternStatusDto.FERDIGSTILT
            EksternStatus.SENDT -> when (eksternKanal) {
                Kanal.SMS -> EksternStatusDto.VELLYKKET_SMS
                Kanal.EPOST -> EksternStatusDto.VELLYKKET_EPOST
                null -> throw IllegalStateException("EksternStatus.SENDT krever at eksternKanal er satt")
            }
            EksternStatus.FEILET -> EksternStatusDto.FEIL
        },
        eksternFeilmelding = eksternFeilmelding,
        eksternKanal = eksternKanal
    )

    fun save(jdbcClient: JdbcClient) {
        require(dbid != null)
        jdbcClient.sql("""
            update minside_varsel
            set
                bestilt = :bestilt,
                minside_status = :minside_status,
                ekstern_status = :ekstern_status,
                ekstern_kanal = :ekstern_kanal,
                ekstern_feilmelding = :ekstern_feilmelding
            where dbid = :dbid
        """.trimIndent())
            .param("dbid", dbid)
            .param("bestilt", bestilt)
            .param("minside_status", minsideStatus?.name)
            .param("ekstern_status", eksternStatus?.name)
            .param("ekstern_kanal", eksternKanal?.name)
            .param("ekstern_feilmelding", eksternFeilmelding)
            .update()
    }

    fun insert(jdbcClient: JdbcClient) {
        jdbcClient.sql("""
            insert into minside_varsel (
                opprettet,
                mottaker_fnr,
                avsender_navident,
                varsel_id,
                mal,
                stilling_id,
                bestilt,
                minside_status,
                ekstern_status,
                ekstern_kanal,
                ekstern_feilmelding
            ) values (
                :opprettet,
                :mottaker_fnr,
                :avsender_navident,
                :varsel_id,
                :mal,
                :stilling_id,
                :bestilt,
                :minside_status,
                :ekstern_status,
                :ekstern_kanal,
                :ekstern_feilmelding
            )
        """.trimIndent())
            .param("opprettet", opprettet)
            .param("mottaker_fnr", mottakerFnr)
            .param("avsender_navident", avsenderNavIdent)
            .param("varsel_id", varselId)
            .param("mal", malMedParametere())

            // TODO: Bytte feltnavn i databasen til avsender_referanse_id, avventer databaseendring for å ikke forkludre tilbakerullingsmuligheter
            .param("stilling_id", avsenderReferanseId)
            .param("bestilt", bestilt)
            .param("minside_status", minsideStatus?.name)
            .param("ekstern_status", eksternStatus?.name)
            .param("ekstern_kanal", eksternKanal?.name)
            .param("ekstern_feilmelding", eksternFeilmelding)
            .update()
    }

    companion object {
        private val uuidGenerator = Generators.timeBasedEpochGenerator()

        fun create(
            mal: Mal,
            avsenderReferanseId: String,
            mottakerFnr: String,
            avsenderNavident: String,
            varselId: String? = null,
            malParametere: List<MalParameter>? = null
        ) = MinsideVarsel(
            dbid = null,
            mal = mal,
            varselId = varselId ?: uuidGenerator.generate().toString(),
            avsenderReferanseId = avsenderReferanseId,
            opprettet = LocalDateTime.now(ZoneId.of("Europe/Oslo")),
            mottakerFnr = mottakerFnr,
            avsenderNavIdent = avsenderNavident,
            bestilt = false,
            minsideStatus = null,
            eksternStatus = null,
            eksternKanal = null,
            eksternFeilmelding = null,
            malParametere = malParametere
        )

        fun finnOgLåsUsendtVarsel(jdbcClient: JdbcClient): MinsideVarsel? =
            jdbcClient.sql("""
                select * from minside_varsel
                where bestilt = false
                for update skip locked
                limit 1
            """.trimIndent())
                .query(RowMapper)
                .optional()
                .getOrNull()

        fun finnFraVarselId(jdbcClient: JdbcClient, varselId: String): MinsideVarsel? =
            jdbcClient.sql("""
                select * from minside_varsel
                where varsel_id = :varsel_id
                limit 1
            """.trimIndent())
                .param("varsel_id", varselId)
                .query(RowMapper)
                .optional()
                .getOrNull()

        fun finnFraVarselIder(jdbcClient: JdbcClient, varselIder: List<String>): List<MinsideVarsel> =
            if (varselIder.isEmpty()) emptyList()
            else jdbcClient.sql("""
                select * from minside_varsel
                where varsel_id in (:varsel_ider)
            """.trimIndent())
                .param("varsel_ider", varselIder)
                .query(RowMapper)
                .list()

        fun hentVarslerForStilling(jdbcClient: JdbcClient, stillingId: String): List<MinsideVarsel> {
            val stillingMaler = Maler.malerForVarselType(VarselType.STILLING)
            return jdbcClient.sql("""
                select * from minside_varsel 
                where stilling_id = :stilling_id 
                and mal = any(:maler)
            """.trimIndent())
                .param("stilling_id", stillingId)
                .param("maler", stillingMaler.toTypedArray())
                .query(RowMapper)
                .list()
        }

        /**
         * Henter varsler for en gitt rekrutteringstreff-ID.
         * Brukes kun av tester, rekrutteringstreff bruker ikke rest api polling.
         */
        fun hentVarslerForRekrutteringstreff(jdbcClient: JdbcClient, rekrutteringstreffId: String): List<MinsideVarsel> {
            // Mal-feltet kan inneholde parameter-suffix (f.eks. "KANDIDAT_INVITERT_TREFF_ENDRET:NAVN,STED")
            // Bruker starts_with som er effektivt med B-tree indeks på prefiks
            return jdbcClient.sql("""
                select * from minside_varsel 
                where stilling_id = :avsender_referanse_id 
                and (starts_with(mal, 'KANDIDAT_INVITERT_TREFF_ENDRET') or mal = 'KANDIDAT_INVITERT_TREFF')
            """.trimIndent())
                .param("avsender_referanse_id", rekrutteringstreffId)
                .query(RowMapper)
                .list()
        }

        fun hentVarslerForQuery(jdbcClient: JdbcClient, queryRequestDto: QueryRequestDto): List<MinsideVarsel> =
            jdbcClient.sql(""" select * from minside_varsel where mottaker_fnr = :mottaker_fnr """)
                .param("mottaker_fnr", queryRequestDto.fnr)
                .query(RowMapper)
                .list()

        object RowMapper: org.springframework.jdbc.core.RowMapper<MinsideVarsel> {
            override fun mapRow(rs: ResultSet, rowNum: Int): MinsideVarsel {
                val malStreng = rs.getString("mal")
                val (mal, malParametere) = Maler.parseValueOf(malStreng)
                
                return MinsideVarsel(
                    dbid = rs.getLong("dbid"),
                    opprettet = rs.getObject("opprettet", LocalDateTime::class.java),
                    mottakerFnr = rs.getString("mottaker_fnr"),
                    avsenderNavIdent = rs.getString("avsender_navident"),
                    varselId = rs.getString("varsel_id"),
                    mal = mal,
                    avsenderReferanseId = rs.getString("stilling_id"),
                    bestilt = rs.getBoolean("bestilt"),
                    minsideStatus = rs.getString("minside_status")?.let { MinsideStatus.valueOf(it) },
                    eksternStatus = rs.getString("ekstern_status")?.let { EksternStatus.valueOf(it) },
                    eksternKanal = rs.getString("ekstern_kanal")?.let { Kanal.valueOf(it) },
                    eksternFeilmelding = rs.getString("ekstern_feilmelding"),
                    malParametere = malParametere
                )
            }
        }
    }
}
