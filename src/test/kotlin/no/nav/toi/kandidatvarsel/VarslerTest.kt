package no.nav.toi.kandidatvarsel

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.kandidatvarsel.minside.bestillVarsel
import no.nav.toi.kandidatvarsel.minside.sjekkVarselOppdateringer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.util.*


private const val kandidatsokPort = 10000
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SystemStubsExtension::class)
@WireMockTest(httpPort = kandidatsokPort)
class VarslerTest {
    private lateinit var minside: FakeMinside
    private val app = LocalApp()

    @BeforeEach fun beforeEach() {
        minside = FakeMinside()
        app.prepare()
    }

    @SystemStub
    private val variables = EnvironmentVariables(
        "AZURE_APP_CLIENT_ID", "1",
        "AZURE_OPENID_CONFIG_ISSUER", issuer,
        "AZURE_OPENID_CONFIG_ISSUER", issuer,
        "AZURE_OPENID_CONFIG_JWKS_URI", jwksUri,
        "AUTHORIZED_PARTY_NAMES", "party",
        "KANDIDATSOK_API_URL", "http://localhost:$kandidatsokPort",
        "AD_GROUP_REKBIS_UTVIKLER", UUID.randomUUID().toString(),
        "AD_GROUP_REKBIS_ARBEIDSGIVERRETTET", UUID.randomUUID().toString(),
        "AD_GROUP_REKBIS_JOBBSOKERRETTET", UUID.randomUUID().toString()
    )

    @AfterAll fun afterAll() = app.close()

    private val fnr1 = "1".repeat(11)
    private val fnr2 = "2".repeat(11)
    private val fnr3 = "3".repeat(11)
    private val veileder1 = app.userToken(navIdent = "Z1")
    private val stillingId1 = "11111111-1111-1111-1111-111111111111"
    private val stillingId2 = "22222222-2222-2222-2222-222222222222"

    object stillingClient: StillingClient {
        override fun getStilling(stillingId: UUID) = Stilling("Snekker", "Snekkerbua AS")
    }

    @Test
    fun alleStatusoverganger() {
        app.postVarselStilling(
            stillingId = stillingId1,
            fnr = listOf(fnr1, fnr2),
            mal = "VURDERT_SOM_AKTUELL",
            token = veileder1,
        )

        app.getVarselStilling(stillingId1, veileder1).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        assertTrue(bestillVarsel(app.dataSource, stillingClient, minside.producer))
        assertTrue(bestillVarsel(app.dataSource, stillingClient, minside.producer))
        assertFalse(bestillVarsel(app.dataSource, stillingClient, minside.producer))

        val bestillinger = minside.mottatteBestillinger(2).also {
            assertEquals(2, it.size)
            it.values.forEach {
                assertEquals("beskjed", it["type"].asText())
                assertEquals("https://www.nav.no/arbeid/stilling/$stillingId1", it["link"].asText())
                assertEquals("substantial", it["sensitivitet"].asText())
            }
        }

        val varselId1 = bestillinger[fnr1]!!["varselId"].asText()

        minside.varselOpprettet(varselId1)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer)

        app.getVarselStilling(stillingId1, veileder1).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.eksterntVarselBestilt(varselId1)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer)

        app.getVarselStilling(stillingId1, veileder1).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.eksterntVarselSendt(varselId1, "SMS")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer)

        app.getVarselStilling(stillingId1, veileder1).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                println(it)
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("VELLYKKET_SMS", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.eksterntVarselSendt(varselId1, "EPOST")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer)

        app.getVarselStilling(stillingId1, veileder1).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("VELLYKKET_EPOST", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.eksterntVarselFeilet(varselId1, "En feil har skjedd")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer)

        app.getVarselStilling(stillingId1, veileder1).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("FEIL", it["eksternStatus"].asText())
                assertEquals("En feil har skjedd", it["eksternFeilmelding"].asText())
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.varselInaktivert(varselId1)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer)

        app.getVarselStilling(stillingId1, veileder1).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("FEIL", it["eksternStatus"].asText())
                assertEquals("En feil har skjedd", it["eksternFeilmelding"].asText())
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.varselSlettet(varselId1)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer)

        app.getVarselStilling(stillingId1, veileder1).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("SLETTET", it["minsideStatus"].asText())
                assertEquals("FEIL", it["eksternStatus"].asText())
                assertEquals("En feil har skjedd", it["eksternFeilmelding"].asText())
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals("Z1", it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }
    }

    @Test
    fun henterVarslerForStilling() {
        app.postVarselStilling(
            stillingId = stillingId1,
            fnr = listOf(fnr1, fnr2),
            mal = "VURDERT_SOM_AKTUELL",
            token = veileder1,
        )
        app.postVarselStilling(
            stillingId = stillingId2,
            fnr = listOf(fnr2, fnr3),
            mal = "VURDERT_SOM_AKTUELL",
            token = veileder1,
        )

        app.getVarselStilling(stillingId1, veileder1).also { varsler ->
            assertEquals(
                setOf(
                    fnr1 to stillingId1,
                    fnr2 to stillingId1
                ),
                varsler.map { (fnr, varsel) -> fnr to varsel["stillingId"].asText() }.toSet()
            )
        }

        app.getVarselStilling(stillingId2, veileder1).also { varsler ->
            assertEquals(
                setOf(
                    fnr2 to stillingId2,
                    fnr3 to stillingId2
                ),
                varsler.map { (fnr, varsel) -> fnr to varsel["stillingId"].asText() }.toSet()
            )
        }
    }

    @Test
    fun henterVarslerForFnr(wmRuntimeInfo: WireMockRuntimeInfo) {
        wmRuntimeInfo.brukertilgangOk()
        app.postVarselStilling(
            stillingId = stillingId1,
            fnr = listOf(fnr1, fnr2),
            mal = "VURDERT_SOM_AKTUELL",
            token = veileder1,
        )
        app.postVarselStilling(
            stillingId = stillingId2,
            fnr = listOf(fnr2, fnr3),
            mal = "VURDERT_SOM_AKTUELL",
            token = veileder1,
        )

        app.getVarselFnr(fnr1, veileder1).also { varsler ->
            assertEquals(
                setOf(
                    stillingId1 to fnr1,
                ),
                varsler.map { (stillingId, varsel) -> stillingId to varsel["mottakerFnr"].asText() }.toSet()
            )
        }

        app.getVarselFnr(fnr2, veileder1).also { varsler ->
            assertEquals(
                setOf(
                    stillingId1 to fnr2,
                    stillingId2 to fnr2,
                ),
                varsler.map { (stillingId, varsel) -> stillingId to varsel["mottakerFnr"].asText() }.toSet()
            )
        }

        app.getVarselFnr(fnr3, veileder1).also { varsler ->
            assertEquals(
                setOf(
                    stillingId2 to fnr3,
                ),
                varsler.map { (stillingId, varsel) -> stillingId to varsel["mottakerFnr"].asText() }.toSet()
            )
        }
    }
}