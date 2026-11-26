package no.nav.toi.kandidatvarsel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel.Companion.create
import no.nav.toi.kandidatvarsel.minside.sjekkVarselOppdateringer
import no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff
import no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret
import no.nav.toi.kandidatvarsel.minside.VurdertSomAktuell
import no.nav.toi.kandidatvarsel.minside.Mal
import no.nav.toi.kandidatvarsel.minside.Maler
import no.nav.toi.kandidatvarsel.minside.VarselType
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel.Companion.finnFraVarselId
import no.nav.toi.kandidatvarsel.minside.MinsideVarsel.Companion.hentVarslerForRekrutteringstreff
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private val objectMapper = jacksonObjectMapper()

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SystemStubsExtension::class)
class MinsideVarselSvarRapidTest {
    private val app = LocalApp()
    private lateinit var minside: FakeMinside

    @BeforeEach
    fun beforeEach() {
        minside = FakeMinside()
        app.prepare()
    }

    @AfterAll
    fun afterAll() = app.close()

    @SystemStub
    private val variables = EnvironmentVariables(
        "AZURE_APP_CLIENT_ID", "1",
        "AZURE_OPENID_CONFIG_ISSUER", issuer,
        "AZURE_OPENID_CONFIG_ISSUER", issuer,
        "AZURE_OPENID_CONFIG_JWKS_URI", jwksUri,
        "AD_GROUP_REKBIS_UTVIKLER", UUID.randomUUID().toString(),
        "AD_GROUP_REKBIS_ARBEIDSGIVERRETTET", UUID.randomUUID().toString(),
        "AD_GROUP_REKBIS_JOBBSOKERRETTET", UUID.randomUUID().toString()
    )

    private val fnr = "12345678910"
    private val rekrutteringstreffId = UUID.randomUUID().toString()
    private val navident = "Z123456"

    @Test
    fun `skal publisere melding på rapid når rekrutteringstreff-varsel får SENDT status`() {
        // Opprett varsel
        app.dataSource.transaction { tx ->
            create(
                mal = KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident
            ).insert(tx)
        }

        val varsel = app.dataSource.transaction { tx ->
            hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId).first()
        }

        minside.eksterntVarselSendt(varsel.varselId, "SMS")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(1, app.testRapid.inspektør.size, "Skal ha publisert 1 melding på rapid")
        val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(0).toString())

        assertEquals(
            setOf(
                "@event_name", "varselId", "avsenderReferanseId", "fnr", "eksternStatus",
                "minsideStatus", "opprettet", "avsenderNavident", "eksternFeilmelding", "eksternKanal", "mal", "type"
            ),
            packet.keys
        )

        // Verifiser alle forventede felter
        assertEquals("minsideVarselSvar", packet["@event_name"])
        assertNotNull(packet["varselId"], "varselId skal være satt")
        assertTrue(packet["varselId"].toString().startsWith("A"), "varselId skal ha prefix 'A'")
        assertEquals(rekrutteringstreffId, packet["avsenderReferanseId"])
        assertEquals(fnr, packet["fnr"])
        assertEquals(navident, packet["avsenderNavident"])
        assertEquals("KANDIDAT_INVITERT_TREFF", packet["mal"])
        assertEquals("SENDT", packet["eksternStatus"])
        assertEquals("UNDER_UTSENDING", packet["minsideStatus"])
        assertNull(packet["eksternFeilmelding"])
        assertEquals("SMS", packet["eksternKanal"])

        // Verifiser at opprettet er en nylig ZonedDateTime-streng (mellom ett minutt siden og nå)
        val opprettet = ZonedDateTime.parse(packet["opprettet"] as String, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val now = ZonedDateTime.now()
        assertTrue(opprettet.isAfter(now.minusMinutes(1)), "opprettet skal være etter ett minutt siden")
        assertTrue(opprettet.isBefore(now.plusSeconds(1)), "opprettet skal være før nå")
    }

    @Test
    fun `skal publisere melding på rapid når rekrutteringstreff-varsel får FEILET status`() {
        // Opprett varsel
        app.dataSource.transaction { tx ->
            create(
                mal = KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident
            ).insert(tx)
        }

        val varsel = app.dataSource.transaction { tx ->
            hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId).first()
        }

        minside.eksterntVarselFeilet(varsel.varselId, "Kunne ikke sende SMS")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(1, app.testRapid.inspektør.size, "Skal ha publisert 1 melding på rapid")
        val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(0).toString())

        assertEquals("minsideVarselSvar", packet["@event_name"])
        assertEquals(rekrutteringstreffId, packet["avsenderReferanseId"])
        assertEquals(fnr, packet["fnr"])
        assertEquals("KANDIDAT_INVITERT_TREFF", packet["mal"])
        assertEquals("FEILET", packet["eksternStatus"])
        assertEquals("Kunne ikke sende SMS", packet["eksternFeilmelding"])
    }

    @Test
    fun `skal publisere minsideVarselSvar med type REKRUTTERINGSTREFF for alle rekrutteringstreff-maler`() {
        val maler = listOf(
            KandidatInvitertTreff,
            KandidatInvitertTreffEndret
        )
        maler.forEach { mal ->
            val varselId = UUID.randomUUID().toString()
            app.dataSource.transaction { tx ->
                create(
                    mal = mal,
                    avsenderReferanseId = rekrutteringstreffId,
                    mottakerFnr = fnr,
                    avsenderNavident = navident,
                    varselId = varselId
                ).insert(tx)
            }
            minside.eksterntVarselSendt(varselId, "SMS")
        }

        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(maler.size, app.testRapid.inspektør.size)
        (0 until app.testRapid.inspektør.size).forEach { i ->
            val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(i).toString())
            assertEquals("minsideVarselSvar", packet["@event_name"])
            assertEquals("REKRUTTERINGSTREFF", packet["type"], "Feil type for mal ${packet["mal"]}")
        }
    }

    @Test
    fun `skal IKKE publisere melding på rapid når rekrutteringstreff-varsel får OPPRETTET status`() {
        // Opprett varsel
        app.dataSource.transaction { tx ->
            create(
                mal = KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident
            ).insert(tx)
        }

        val varsel = app.dataSource.transaction { tx ->
            hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId).first()
        }

        minside.varselOpprettet(varsel.varselId)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(0, app.testRapid.inspektør.size, "Skal IKKE ha publisert noen meldinger på rapid")
    }

    @Test
    fun `skal IKKE publisere melding på rapid når rekrutteringstreff-varsel får BESTILT status`() {
        // Opprett varsel
        app.dataSource.transaction { tx ->
            create(
                mal = KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident
            ).insert(tx)
        }

        val varsel = app.dataSource.transaction { tx ->
            hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId).first()
        }

        minside.eksterntVarselBestilt(varsel.varselId)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(0, app.testRapid.inspektør.size, "Skal IKKE ha publisert noen meldinger på rapid")
    }

    @Test
    fun `skal publisere melding på rapid når endret rekrutteringstreff-varsel får SENDT status`() {
        val varselId = UUID.randomUUID().toString()
        // Opprett varsel
        app.dataSource.transaction { tx ->
            create(
                mal = KandidatInvitertTreffEndret,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident,
                varselId = varselId
            ).insert(tx)
        }

        minside.eksterntVarselSendt(varselId, "SMS")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(1, app.testRapid.inspektør.size, "Skal ha publisert 1 melding på rapid")
        val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(0).toString())

        // Verifiser alle forventede felter
        assertEquals("minsideVarselSvar", packet["@event_name"])
        assertNotNull(packet["varselId"])
        assertEquals(rekrutteringstreffId, packet["avsenderReferanseId"])
        assertEquals(fnr, packet["fnr"])
        assertEquals(navident, packet["avsenderNavident"])
        assertEquals("KANDIDAT_INVITERT_TREFF_ENDRET", packet["mal"])
        assertEquals("SENDT", packet["eksternStatus"])
        assertEquals("SMS", packet["eksternKanal"])

        // Verifiser at opprettet er en nylig ZonedDateTime-streng (mellom ett minutt siden og nå)
        val opprettet = ZonedDateTime.parse(packet["opprettet"] as String, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val now = ZonedDateTime.now()
        assertTrue(opprettet.isAfter(now.minusMinutes(1)), "opprettet skal være etter ett minutt siden")
        assertTrue(opprettet.isBefore(now.plusSeconds(1)), "opprettet skal være før nå")
    }

    @Test
    fun `skal publisere melding med flettedata på rapid når endret rekrutteringstreff-varsel får SENDT status`() {
        val varselId = UUID.randomUUID().toString()
        val endringsTekster = listOf("navn", "tidspunkt")

        // Opprett varsel med flettedata (displayTekster for endringene)
        app.dataSource.transaction { tx ->
            create(
                mal = KandidatInvitertTreffEndret,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident,
                varselId = varselId,
                flettedata = endringsTekster
            ).insert(tx)
        }

        minside.eksterntVarselSendt(varselId, "SMS")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(1, app.testRapid.inspektør.size, "Skal ha publisert 1 melding på rapid")
        val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(0).toString())

        // Verifiser alle forventede felter
        assertEquals("minsideVarselSvar", packet["@event_name"])
        assertEquals(rekrutteringstreffId, packet["avsenderReferanseId"])
        assertEquals(fnr, packet["fnr"])
        assertEquals("KANDIDAT_INVITERT_TREFF_ENDRET", packet["mal"])
        assertEquals("SENDT", packet["eksternStatus"])

        // Verifiser at flettedata er med i meldingen
        @Suppress("UNCHECKED_CAST")
        val rapidFlettedata = packet["flettedata"] as? List<String>
        assertNotNull(rapidFlettedata, "flettedata skal være med i rapid-meldingen")
        assertEquals(listOf("navn", "tidspunkt"), rapidFlettedata)
    }

    @Test
    fun `skal lese flettedata fra JSONB med MinsideVarsel sin RowMapper`() {
        val varselId = UUID.randomUUID().toString()
        val jsonArray = """["navn","tidspunkt"]"""

        app.dataSource.transaction { tx ->
            create(
                mal = KandidatInvitertTreffEndret,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident,
                varselId = varselId
            ).insert(tx)

            tx.sql(
                """
                update minside_varsel
                set flettedata = CAST(:json AS jsonb)
                where varsel_id = :varsel_id
            """.trimIndent()
            )
                .param("json", jsonArray)
                .param("varsel_id", varselId)
                .update()
        }

        val varsel = app.dataSource.transaction { tx ->
            finnFraVarselId(tx, varselId)
        }

        assertEquals(listOf("navn", "tidspunkt"), varsel?.flettedata)
    }

    private fun opprettStillingVarsel(mal: Mal = VurdertSomAktuell, stillingId: String = UUID.randomUUID().toString()): String {
        val varselId = UUID.randomUUID().toString()
        app.dataSource.transaction { tx ->
            create(
                mal = mal,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr,
                avsenderNavident = navident,
                varselId = varselId
            ).insert(tx)
        }
        return varselId
    }

    @Test
    fun `skal publisere melding på rapid når stilling-varsel får SENDT status`() {
        val stillingId = UUID.randomUUID().toString()
        val varselId = opprettStillingVarsel(stillingId = stillingId)

        minside.eksterntVarselSendt(varselId, "SMS")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(1, app.testRapid.inspektør.size, "Skal ha publisert 1 melding på rapid")
        val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(0).toString())

        assertEquals(
            setOf(
                "@event_name", "varselId", "avsenderReferanseId", "fnr", "eksternStatus",
                "minsideStatus", "opprettet", "avsenderNavident", "eksternFeilmelding", "eksternKanal", "mal", "type"
            ),
            packet.keys
        )
        assertEquals("minsideVarselSvar", packet["@event_name"])
        assertTrue(packet["varselId"].toString().startsWith("A"), "varselId skal ha prefix 'A'")
        assertEquals(stillingId, packet["avsenderReferanseId"])
        assertEquals(fnr, packet["fnr"])
        assertEquals(navident, packet["avsenderNavident"])
        assertEquals("VURDERT_SOM_AKTUELL", packet["mal"])
        assertEquals("STILLING", packet["type"])
        assertEquals("SENDT", packet["eksternStatus"])
        assertEquals("UNDER_UTSENDING", packet["minsideStatus"])
        assertNull(packet["eksternFeilmelding"])
        assertEquals("SMS", packet["eksternKanal"])
    }

    @Test
    fun `skal publisere minsideVarselSvar med type STILLING for alle stilling-maler`() {
        val maler = Maler.malerForVarselType(VarselType.STILLING).map(Maler::valueOf)
        maler.forEach { mal ->
            minside.eksterntVarselSendt(opprettStillingVarsel(mal), "SMS")
        }

        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(maler.size, app.testRapid.inspektør.size)
        val publiserteMaler = (0 until app.testRapid.inspektør.size).map { i ->
            val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(i).toString())
            assertEquals("minsideVarselSvar", packet["@event_name"])
            assertEquals("STILLING", packet["type"], "Feil type for mal ${packet["mal"]}")
            packet["mal"]
        }
        assertEquals(maler.map { it.name }.toSet(), publiserteMaler.toSet())
    }

    @Test
    fun `skal publisere melding på rapid når stilling-varsel får FEILET status`() {
        val varselId = opprettStillingVarsel()

        minside.eksterntVarselFeilet(varselId, "Kunne ikke sende SMS")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(1, app.testRapid.inspektør.size, "Skal ha publisert 1 melding på rapid")
        val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(0).toString())
        assertEquals("STILLING", packet["type"])
        assertEquals("FEILET", packet["eksternStatus"])
        assertEquals("Kunne ikke sende SMS", packet["eksternFeilmelding"])
    }

    @Test
    fun `skal IKKE publisere melding på rapid når stilling-varsel får BESTILT status`() {
        val varselId = opprettStillingVarsel()

        minside.eksterntVarselBestilt(varselId)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(0, app.testRapid.inspektør.size, "Skal IKKE ha publisert noen meldinger på rapid")
    }

    @Test
    fun `skal publisere melding med EPOST kanal på rapid når rekrutteringstreff-varsel sendes via epost`() {
        val varselId = UUID.randomUUID().toString()

        app.dataSource.transaction { tx ->
            create(
                mal = KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident,
                varselId = varselId
            ).insert(tx)
        }

        minside.eksterntVarselSendt(varselId, "EPOST")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(1, app.testRapid.inspektør.size, "Skal ha publisert 1 melding på rapid")
        val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(0).toString())

        assertEquals("minsideVarselSvar", packet["@event_name"])
        assertEquals("SENDT", packet["eksternStatus"])
        assertEquals("EPOST", packet["eksternKanal"])
    }
}
