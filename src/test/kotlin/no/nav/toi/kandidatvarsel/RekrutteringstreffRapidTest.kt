package no.nav.toi.kandidatvarsel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.kandidatvarsel.minside.sjekkVarselOppdateringer
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

private const val kandidatsokPort = 10003

private val objectMapper = jacksonObjectMapper()

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SystemStubsExtension::class)
@WireMockTest(httpPort = kandidatsokPort)
class RekrutteringstreffRapidTest {
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
        "KANDIDATSOK_API_URL", "http://localhost:$kandidatsokPort",
        "AD_GROUP_REKBIS_UTVIKLER", UUID.randomUUID().toString(),
        "AD_GROUP_REKBIS_ARBEIDSGIVERRETTET", UUID.randomUUID().toString(),
        "AD_GROUP_REKBIS_JOBBSOKERRETTET", UUID.randomUUID().toString()
    )

    private val fnr = "12345678910"
    private val rekrutteringstreffId = UUID.randomUUID().toString()
    private val navident = "Z123456"

    @Test
    fun `skal publisere melding på rapid når rekrutteringstreff-varsel får FERDIGSTILT status`() {
        // Opprett varsel
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident
            ).insert(tx)
        }

        val varsel = app.dataSource.transaction { tx ->
             no.nav.toi.kandidatvarsel.minside.MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId).first()
        }
        
        minside.eksterntVarselFerdigstilt(varsel.varselId)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(1, app.testRapid.inspektør.size, "Skal ha publisert 1 melding på rapid")
        val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(0).toString())
        
        // Verifiser alle forventede felter
        assertEquals("minsideVarselSvar", packet["@event_name"])
        assertNotNull(packet["varselId"], "varselId skal være satt")
        assertTrue(packet["varselId"].toString().startsWith("A"), "varselId skal ha prefix 'A'")
        assertEquals(rekrutteringstreffId, packet["avsenderReferanseId"])
        assertEquals(fnr, packet["fnr"])
        assertEquals(navident, packet["avsenderNavident"])
        assertEquals("KANDIDAT_INVITERT_TREFF", packet["mal"])
        assertEquals("FERDIGSTILT", packet["eksternStatus"])
        
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
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident
            ).insert(tx)
        }

        val varsel = app.dataSource.transaction { tx ->
             no.nav.toi.kandidatvarsel.minside.MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId).first()
        }
        
        minside.eksterntVarselFeilet(varsel.varselId, "Kunne ikke sende SMS")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(1, app.testRapid.inspektør.size, "Skal ha publisert 1 melding på rapid")
        val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(0).toString())
        
        assertEquals("minsideVarselSvar", packet["@event_name"])
        assertEquals(rekrutteringstreffId, packet["avsenderReferanseId"])
        assertEquals(fnr, packet["fnr"])
        assertEquals("KANDIDAT_INVITERT_TREFF", packet["mal"])
        assertEquals("FEIL", packet["eksternStatus"])
        assertEquals("Kunne ikke sende SMS", packet["eksternFeilmelding"])
    }

    @Test
    fun `skal IKKE publisere melding på rapid når rekrutteringstreff-varsel får OPPRETTET status`() {
        // Opprett varsel
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident
            ).insert(tx)
        }

        val varsel = app.dataSource.transaction { tx ->
             no.nav.toi.kandidatvarsel.minside.MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId).first()
        }
        
        minside.varselOpprettet(varsel.varselId)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(0, app.testRapid.inspektør.size, "Skal IKKE ha publisert noen meldinger på rapid")
    }

    @Test
    fun `skal IKKE publisere melding på rapid når rekrutteringstreff-varsel får BESTILT status`() {
        // Opprett varsel
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident
            ).insert(tx)
        }

        val varsel = app.dataSource.transaction { tx ->
             no.nav.toi.kandidatvarsel.minside.MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId).first()
        }
        
        minside.eksterntVarselBestilt(varsel.varselId)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(0, app.testRapid.inspektør.size, "Skal IKKE ha publisert noen meldinger på rapid")
    }

    @Test
    fun `skal IKKE publisere melding på rapid når rekrutteringstreff-varsel får SENDT status`() {
        // Opprett varsel
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident
            ).insert(tx)
        }

        val varsel = app.dataSource.transaction { tx ->
             no.nav.toi.kandidatvarsel.minside.MinsideVarsel.hentVarslerForRekrutteringstreff(tx, rekrutteringstreffId).first()
        }
        
        minside.eksterntVarselSendt(varsel.varselId, "SMS")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(0, app.testRapid.inspektør.size, "Skal IKKE ha publisert noen meldinger på rapid")
    }

    @Test
    fun `skal publisere melding på rapid når endret rekrutteringstreff-varsel får FERDIGSTILT status`() {
        val varselId = UUID.randomUUID().toString()
        // Opprett varsel
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident,
                varselId = varselId
            ).insert(tx)
        }

        minside.eksterntVarselFerdigstilt(varselId)
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
        assertEquals("FERDIGSTILT", packet["eksternStatus"])
        
        // Verifiser at opprettet er en nylig ZonedDateTime-streng (mellom ett minutt siden og nå)
        val opprettet = ZonedDateTime.parse(packet["opprettet"] as String, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val now = ZonedDateTime.now()
        assertTrue(opprettet.isAfter(now.minusMinutes(1)), "opprettet skal være etter ett minutt siden")
        assertTrue(opprettet.isBefore(now.plusSeconds(1)), "opprettet skal være før nå")
    }
    
    @Test
    fun `skal publisere melding med malParametere på rapid når endret rekrutteringstreff-varsel får FERDIGSTILT status`() {
        val varselId = UUID.randomUUID().toString()
        val malParametere = listOf(
            no.nav.toi.kandidatvarsel.minside.MalParameter.NAVN,
            no.nav.toi.kandidatvarsel.minside.MalParameter.TIDSPUNKT
        )
        
        // Opprett varsel med malParametere
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr,
                avsenderNavident = navident,
                varselId = varselId,
                malParametere = malParametere
            ).insert(tx)
        }

        minside.eksterntVarselFerdigstilt(varselId)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(1, app.testRapid.inspektør.size, "Skal ha publisert 1 melding på rapid")
        val packet = objectMapper.readValue<Map<String, Any?>>(app.testRapid.inspektør.message(0).toString())
        
        // Verifiser alle forventede felter
        assertEquals("minsideVarselSvar", packet["@event_name"])
        assertEquals(rekrutteringstreffId, packet["avsenderReferanseId"])
        assertEquals(fnr, packet["fnr"])
        assertEquals("KANDIDAT_INVITERT_TREFF_ENDRET", packet["mal"])
        assertEquals("FERDIGSTILT", packet["eksternStatus"])
        
        // Verifiser at malParametere er med i meldingen
        @Suppress("UNCHECKED_CAST")
        val rapidMalParametere = packet["malParametere"] as? List<String>
        assertNotNull(rapidMalParametere, "malParametere skal være med i rapid-meldingen")
        assertEquals(listOf("NAVN", "TIDSPUNKT"), rapidMalParametere)
    }

    @Test
    fun `skal IKKE publisere melding på rapid når stilling-varsel får FERDIGSTILT status`() {
        val varselId = UUID.randomUUID().toString()
        val stillingId = UUID.randomUUID().toString()
        // Opprett varsel
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.VurdertSomAktuell,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr,
                avsenderNavident = navident,
                varselId = varselId
            ).insert(tx)
        }

        minside.eksterntVarselFerdigstilt(varselId)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.testRapid)

        assertEquals(0, app.testRapid.inspektør.size, "Skal IKKE ha publisert noen meldinger på rapid")
    }
}
