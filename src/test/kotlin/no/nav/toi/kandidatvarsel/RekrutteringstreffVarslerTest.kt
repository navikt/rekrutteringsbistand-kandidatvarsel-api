package no.nav.toi.kandidatvarsel

import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.util.*


private const val kandidatsokPort = 10001
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SystemStubsExtension::class)
@WireMockTest(httpPort = kandidatsokPort)
class RekrutteringstreffVarslerTest {
    private val app = LocalApp()

    @BeforeEach fun beforeEach() = app.prepare()
    @AfterAll fun afterAll() = app.close()

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

    private val fnr1 = "1".repeat(11)
    private val fnr2 = "2".repeat(11)
    private val fnr3 = "3".repeat(11)
    private val veileder1 = app.userToken(navIdent = "Z1")
    private val rekrutteringstreffId1 = "11111111-1111-1111-1111-111111111111"
    private val rekrutteringstreffId2 = "22222222-2222-2222-2222-222222222222"

    @Test
    fun `henter varsler for rekrutteringstreff`() {
        // Opprett varsler direkte i databasen
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId1,
                mottakerFnr = fnr1,
                avsenderNavident = "Z1"
            ).insert(tx)

            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.InvitertKandidatTreffEndret,
                avsenderReferanseId = rekrutteringstreffId1,
                mottakerFnr = fnr2,
                avsenderNavident = "Z1"
            ).insert(tx)

            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId2,
                mottakerFnr = fnr3,
                avsenderNavident = "Z1"
            ).insert(tx)
        }

        app.getVarselRekrutteringstreff(rekrutteringstreffId1, veileder1).also { varsler ->
            assertEquals(2, varsler.size)
            assertEquals(fnr1, varsler[fnr1]!!["mottakerFnr"].asText())
            assertEquals(rekrutteringstreffId1, varsler[fnr1]!!["stillingId"].asText())
            assertEquals(fnr2, varsler[fnr2]!!["mottakerFnr"].asText())
            assertEquals(rekrutteringstreffId1, varsler[fnr2]!!["stillingId"].asText())
        }

        app.getVarselRekrutteringstreff(rekrutteringstreffId2, veileder1).also { varsler ->
            assertEquals(1, varsler.size)
            assertEquals(fnr3, varsler[fnr3]!!["mottakerFnr"].asText())
            assertEquals(rekrutteringstreffId2, varsler[fnr3]!!["stillingId"].asText())
        }
    }

    @Test
    fun `rekrutteringstreff endpoint filtrerer kun rekrutteringstreff-maler`() {
        val rekrutteringstreffId = "99999999-9999-9999-9999-999999999999"

        // Opprett varsler direkte i databasen
        app.dataSource.transaction { tx ->
            // Rekrutteringstreff-mal
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr1,
                avsenderNavident = "Z1"
            ).insert(tx)

            // Stilling-mal med samme id (for å teste filtrering)
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.VurdertSomAktuell,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr2,
                avsenderNavident = "Z1"
            ).insert(tx)

            // En annen stilling-mal
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.PassendeStilling,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr3,
                avsenderNavident = "Z1"
            ).insert(tx)
        }

        // Hent varsler for rekrutteringstreffId - skal kun returnere rekrutteringstreff-maler
        app.getVarselRekrutteringstreff(rekrutteringstreffId, veileder1).also { varsler ->
            assertEquals(1, varsler.size)
            assertEquals(fnr1, varsler[fnr1]!!["mottakerFnr"].asText())
            assertEquals(rekrutteringstreffId, varsler[fnr1]!!["stillingId"].asText())
        }
    }

    @Test
    fun `returnerer tomt array når ingen varsler finnes for rekrutteringstreff`() {
        val rekrutteringstreffId = "77777777-7777-7777-7777-777777777777"

        app.getVarselRekrutteringstreff(rekrutteringstreffId, veileder1).also { varsler ->
            assertEquals(0, varsler.size)
        }
    }

    @Test
    fun `varsler for rekrutteringstreff har korrekt status`() {
        val rekrutteringstreffId = "66666666-6666-6666-6666-666666666666"

        // Opprett varsel direkte i databasen
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr1,
                avsenderNavident = "Z1"
            ).insert(tx)
        }

        app.getVarselRekrutteringstreff(rekrutteringstreffId, veileder1).also { varsler ->
            assertEquals(1, varsler.size)
            val varsel = varsler[fnr1]!!
            assertEquals("UNDER_UTSENDING", varsel["minsideStatus"].asText())
            assertEquals("UNDER_UTSENDING", varsel["eksternStatus"].asText())
            assertEquals("Z1", varsel["avsenderNavident"].asText())
        }
    }

    @Test
    fun `begge rekrutteringstreff-maler vises i samme resultat`() {
        val rekrutteringstreffId = "55555555-5555-5555-5555-555555555555"

        // Opprett varsler med begge maltyper til forskjellige personer
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr1,
                avsenderNavident = "Z1"
            ).insert(tx)

            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.InvitertKandidatTreffEndret,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr2,
                avsenderNavident = "Z1"
            ).insert(tx)
        }

        app.getVarselRekrutteringstreff(rekrutteringstreffId, veileder1).also { varsler ->
            assertEquals(2, varsler.size)
            assertEquals(fnr1, varsler[fnr1]!!["mottakerFnr"].asText())
            assertEquals(fnr2, varsler[fnr2]!!["mottakerFnr"].asText())
        }
    }
}
