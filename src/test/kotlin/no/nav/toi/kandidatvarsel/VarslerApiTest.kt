package no.nav.toi.kandidatvarsel

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import no.nav.toi.kandidatvarsel.minside.bestillVarsel
import no.nav.toi.kandidatvarsel.minside.sjekkVarselOppdateringer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*


private const val kandidatsokPort = 10002

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SystemStubsExtension::class)
@WireMockTest(httpPort = kandidatsokPort)
class VarslerApiTest {
    private val app = LocalApp()
    private val httpClient = HttpClient.newBuilder().build()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
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

    private val navident = "Z999999"
    private val token = app.userToken(navIdent = navident)
    private val fnr1 = "11111111111"
    private val fnr2 = "22222222222"
    private val fnr3 = "33333333333"
    private val stillingId1 = "11111111-1111-1111-1111-111111111111"
    private val stillingId2 = "22222222-2222-2222-2222-222222222222"

    object stillingClient: StillingClient {
        override fun getStilling(stillingId: UUID) = Stilling("Snekker", "Snekkerbua AS")
    }

    @Test
    fun `GET stilling endepunkt returnerer varsler for stillingId`() {
        val stillingId = UUID.randomUUID().toString()

        // Opprett varsler direkte i databasen
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.VurdertSomAktuell,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr1,
                avsenderNavident = navident
            ).insert(tx)

            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.PassendeStilling,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr2,
                avsenderNavident = navident
            ).insert(tx)
        }

        // Kall endepunkt
        val response = httpGet("/api/varsler/stilling/$stillingId", token)

        // Verifiser respons
        assertEquals(200, response.statusCode())
        
        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(2, varsler.size)
        
        val mottakerFnrListe = varsler.map { it["mottakerFnr"] as String }
        assertTrue(mottakerFnrListe.contains(fnr1))
        assertTrue(mottakerFnrListe.contains(fnr2))
        
        // Verifiser at alle varsler tilhører riktig stilling
        varsler.forEach { varsel ->
            assertEquals(stillingId, varsel["stillingId"] as String)
            assertEquals(navident, varsel["avsenderNavident"] as String)
            assertEquals("UNDER_UTSENDING", varsel["minsideStatus"] as String)
            assertEquals("UNDER_UTSENDING", varsel["eksternStatus"] as String)
        }
    }

    @Test
    fun `GET rekrutteringstreff endepunkt returnerer varsler for rekrutteringstreffId`() {
        val rekrutteringstreffId = UUID.randomUUID().toString()

        // Opprett varsler direkte i databasen
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr1,
                avsenderNavident = navident
            ).insert(tx)

            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr2,
                avsenderNavident = navident
            ).insert(tx)
        }

        // Kall endepunkt
        val response = httpGet("/api/varsler/rekrutteringstreff/$rekrutteringstreffId", token)

        // Verifiser respons
        assertEquals(200, response.statusCode())
        
        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(2, varsler.size)
        
        val mottakerFnrListe = varsler.map { it["mottakerFnr"] as String }
        assertTrue(mottakerFnrListe.contains(fnr1))
        assertTrue(mottakerFnrListe.contains(fnr2))
        
        // Verifiser at alle varsler tilhører riktig rekrutteringstreff
        varsler.forEach { varsel ->
            assertEquals(rekrutteringstreffId, varsel["stillingId"] as String)
            assertEquals(navident, varsel["avsenderNavident"] as String)
            assertEquals("UNDER_UTSENDING", varsel["minsideStatus"] as String)
            assertEquals("UNDER_UTSENDING", varsel["eksternStatus"] as String)
        }
    }

    @Test
    fun `GET stilling endepunkt returnerer tomt array når ingen varsler finnes`() {
        val stillingId = UUID.randomUUID().toString()

        // Kall endepunkt uten å opprette varsler
        val response = httpGet("/api/varsler/stilling/$stillingId", token)

        assertEquals(200, response.statusCode())
        
        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(0, varsler.size)
    }

    @Test
    fun `GET rekrutteringstreff endepunkt returnerer tomt array når ingen varsler finnes`() {
        val rekrutteringstreffId = UUID.randomUUID().toString()

        // Kall endepunkt uten å opprette varsler
        val response = httpGet("/api/varsler/rekrutteringstreff/$rekrutteringstreffId", token)

        assertEquals(200, response.statusCode())
        
        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(0, varsler.size)
    }

    @Test
    fun `POST stilling endepunkt oppretter varsler`() {
        val stillingId = UUID.randomUUID().toString()
        val body = objectMapper.writeValueAsString(
            mapOf(
                "fnr" to listOf(fnr1, fnr2),
                "mal" to "VURDERT_SOM_AKTUELL"
            )
        )

        val response = httpPost("/api/varsler/stilling/$stillingId", body, token)
        assertEquals(201, response.statusCode())

        // Verifiser at varsler ble opprettet
        val getResponse = httpGet("/api/varsler/stilling/$stillingId", token)
        assertEquals(200, getResponse.statusCode())
        
        val varsler: List<Map<String, Any?>> = objectMapper.readValue(getResponse.body())
        assertEquals(2, varsler.size)
    }

    @Test
    fun `POST query endepunkt returnerer varsler for fnr`(wmRuntimeInfo: WireMockRuntimeInfo) {
        wmRuntimeInfo.brukertilgangOk()
        val stillingId = UUID.randomUUID().toString()
        
        // Opprett varsel
        app.postVarselStilling(
            stillingId = stillingId,
            fnr = listOf(fnr1),
            mal = "VURDERT_SOM_AKTUELL",
            token = token,
        )

        // Query varsler for fnr
        val body = objectMapper.writeValueAsString(mapOf("fnr" to fnr1))
        val response = httpPost("/api/varsler/query", body, token)
        
        assertEquals(200, response.statusCode())
        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(1, varsler.size)
        assertEquals(fnr1, varsler[0]["mottakerFnr"])
    }

    @Test
    fun `POST query endepunkt returnerer tomt array når ingen varsler finnes for fnr`(wmRuntimeInfo: WireMockRuntimeInfo) {
        wmRuntimeInfo.brukertilgangOk()
        
        // Query varsler for fnr som ikke har varsler
        val body = objectMapper.writeValueAsString(mapOf("fnr" to fnr1))
        val response = httpPost("/api/varsler/query", body, token)
        
        assertEquals(200, response.statusCode())
        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(0, varsler.size)
    }

    @Test
    fun `POST stilling endepunkt med tom fnr liste oppretter ingen varsler`() {
        val stillingId = UUID.randomUUID().toString()
        val body = objectMapper.writeValueAsString(
            mapOf(
                "fnr" to emptyList<String>(),
                "mal" to "VURDERT_SOM_AKTUELL"
            )
        )

        val response = httpPost("/api/varsler/stilling/$stillingId", body, token)
        assertEquals(201, response.statusCode())

        // Verifiser at ingen varsler ble opprettet
        val getResponse = httpGet("/api/varsler/stilling/$stillingId", token)
        assertEquals(200, getResponse.statusCode())
        
        val varsler: List<Map<String, Any?>> = objectMapper.readValue(getResponse.body())
        assertEquals(0, varsler.size)
    }

    @Test
    fun `POST stilling endepunkt med forskjellige maler`() {
        val stillingId = UUID.randomUUID().toString()
        
        // Opprett varsel med VURDERT_SOM_AKTUELL
        val body1 = objectMapper.writeValueAsString(
            mapOf(
                "fnr" to listOf(fnr1),
                "mal" to "VURDERT_SOM_AKTUELL"
            )
        )
        val response1 = httpPost("/api/varsler/stilling/$stillingId", body1, token)
        assertEquals(201, response1.statusCode())

        // Opprett varsel med PASSENDE_STILLING
        val body2 = objectMapper.writeValueAsString(
            mapOf(
                "fnr" to listOf(fnr2),
                "mal" to "PASSENDE_STILLING"
            )
        )
        val response2 = httpPost("/api/varsler/stilling/$stillingId", body2, token)
        assertEquals(201, response2.statusCode())

        // Verifiser at begge varsler ble opprettet
        val getResponse = httpGet("/api/varsler/stilling/$stillingId", token)
        assertEquals(200, getResponse.statusCode())
        
        val varsler: List<Map<String, Any?>> = objectMapper.readValue(getResponse.body())
        assertEquals(2, varsler.size)
    }

    @Test
    fun alleStatusoverganger() {
        app.postVarselStilling(
            stillingId = stillingId1,
            fnr = listOf(fnr1, fnr2),
            mal = "VURDERT_SOM_AKTUELL",
            token = token,
        )

        app.getVarselStilling(stillingId1, token).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
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
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.mockKafkaRapid)

        app.getVarselStilling(stillingId1, token).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
                assertTrue(it["eksternKanal"].isNull)
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
                assertTrue(it["eksternKanal"].isNull)
            }
        }

        minside.eksterntVarselBestilt(varselId1)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.mockKafkaRapid)

        app.getVarselStilling(stillingId1, token).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.eksterntVarselSendt(varselId1, "SMS")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.mockKafkaRapid)

        app.getVarselStilling(stillingId1, token).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("VELLYKKET_SMS", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
                assertTrue(it["eksternKanal"].asText().contains("SMS"))
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.eksterntVarselSendt(varselId1, "EPOST")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.mockKafkaRapid)

        app.getVarselStilling(stillingId1, token).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("VELLYKKET_EPOST", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
                assertTrue(it["eksternKanal"].asText().contains("EPOST"))
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.eksterntVarselFeilet(varselId1, "En feil har skjedd")
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.mockKafkaRapid)

        app.getVarselStilling(stillingId1, token).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("FEIL", it["eksternStatus"].asText())
                assertEquals("En feil har skjedd", it["eksternFeilmelding"].asText())
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.varselInaktivert(varselId1)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.mockKafkaRapid)

        app.getVarselStilling(stillingId1, token).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("OPPRETTET", it["minsideStatus"].asText())
                assertEquals("FEIL", it["eksternStatus"].asText())
                assertEquals("En feil har skjedd", it["eksternFeilmelding"].asText())
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("UNDER_UTSENDING", it["minsideStatus"].asText())
                assertEquals("UNDER_UTSENDING", it["eksternStatus"].asText())
                assertTrue(it["eksternFeilmelding"].isNull)
            }
        }

        minside.varselSlettet(varselId1)
        sjekkVarselOppdateringer(app.dataSource, minside.consumer, app.mockKafkaRapid)

        app.getVarselStilling(stillingId1, token).also { varsler ->
            assertEquals(2, varsler.size)

            varsler[fnr1]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
                assertEquals("SLETTET", it["minsideStatus"].asText())
                assertEquals("FEIL", it["eksternStatus"].asText())
                assertEquals("En feil har skjedd", it["eksternFeilmelding"].asText())
            }

            varsler[fnr2]!!.let {
                assertEquals(stillingId1, it["stillingId"].asText())
                assertEquals(navident, it["avsenderNavident"].asText())
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
            token = token,
        )
        app.postVarselStilling(
            stillingId = stillingId2,
            fnr = listOf(fnr2, fnr3),
            mal = "VURDERT_SOM_AKTUELL",
            token = token,
        )

        app.getVarselStilling(stillingId1, token).also { varsler ->
            assertEquals(
                setOf(
                    fnr1 to stillingId1,
                    fnr2 to stillingId1
                ),
                varsler.map { (fnr, varsel) -> fnr to varsel["stillingId"].asText() }.toSet()
            )
        }

        app.getVarselStilling(stillingId2, token).also { varsler ->
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
            token = token,
        )
        app.postVarselStilling(
            stillingId = stillingId2,
            fnr = listOf(fnr2, fnr3),
            mal = "VURDERT_SOM_AKTUELL",
            token = token,
        )

        app.getVarselFnr(fnr1, token).also { varsler ->
            assertEquals(
                setOf(
                    stillingId1 to fnr1,
                ),
                varsler.map { (stillingId, varsel) -> stillingId to varsel["mottakerFnr"].asText() }.toSet()
            )
        }

        app.getVarselFnr(fnr2, token).also { varsler ->
            assertEquals(
                setOf(
                    stillingId1 to fnr2,
                    stillingId2 to fnr2,
                ),
                varsler.map { (stillingId, varsel) -> stillingId to varsel["mottakerFnr"].asText() }.toSet()
            )
        }

        app.getVarselFnr(fnr3, token).also { varsler ->
            assertEquals(
                setOf(
                    stillingId2 to fnr3,
                ),
                varsler.map { (stillingId, varsel) -> stillingId to varsel["mottakerFnr"].asText() }.toSet()
            )
        }
    }

    @Test
    fun `bestillVarsel kaster IllegalStateException når stilling ikke finnes`() {
        val stillingId = "99999999-9999-9999-9999-999999999999"
        
        // Opprett varsel med stilling-mal
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.VurdertSomAktuell,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr1,
                avsenderNavident = navident
            ).insert(tx)
        }

        // Mock StillingClient som returnerer null
        val stillingClientSomReturnererNull = object : StillingClient {
            override fun getStilling(stillingId: UUID): Stilling? = null
        }

        // Verifiser at vi kaster IllegalStateException
        val exception = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            bestillVarsel(app.dataSource, stillingClientSomReturnererNull, minside.producer)
        }
        
        assertEquals("Kunne ikke hente stilling med id $stillingId", exception.message)
    }

    @Test
    fun `bestillVarsel fungerer for rekrutteringstreff uten å hente stilling`() {
        val rekrutteringstreffId = "88888888-8888-8888-8888-888888888888"
        
        // Opprett varsel med rekrutteringstreff-mal
        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr1,
                avsenderNavident = navident
            ).insert(tx)
        }

        // Mock StillingClient som aldri skal kalles
        val stillingClientSomAldriBurdeKalles = object : StillingClient {
            override fun getStilling(stillingId: UUID): Stilling? {
                throw AssertionError("StillingClient skal ikke kalles for rekrutteringstreff")
            }
        }

        // Verifiser at bestilling fungerer uten å kalle StillingClient
        assertTrue(bestillVarsel(app.dataSource, stillingClientSomAldriBurdeKalles, minside.producer))
        
        // Verifiser at varsel ble bestilt
        val bestillinger = minside.mottatteBestillinger(1)
        assertEquals(1, bestillinger.size)
        assertTrue(bestillinger.containsKey(fnr1))
    }

    @Test
    fun `stilling endpoint filtrerer kun stilling-maler`() {
        val stillingId = "99999999-9999-9999-9999-999999999999"
        val rekrutteringstreffId = "88888888-8888-8888-8888-888888888888"

        // Opprett varsler direkte i databasen
        app.dataSource.transaction { tx ->
            // Stilling-mal
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.VurdertSomAktuell,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr1,
                avsenderNavident = navident
            ).insert(tx)

            // Rekrutteringstreff-mal med samme id (for å teste filtrering)
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr2,
                avsenderNavident = navident
            ).insert(tx)

            // Rekrutteringstreff-mal med annen id
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreffEndret,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr3,
                avsenderNavident = navident
            ).insert(tx)
        }

        // Hent varsler for stillingId - skal kun returnere stilling-maler
        app.getVarselStilling(stillingId, token).also { varsler ->
            assertEquals(1, varsler.size)
            assertEquals(fnr1, varsler[fnr1]!!["mottakerFnr"].asText())
            assertEquals(stillingId, varsler[fnr1]!!["stillingId"].asText())
        }
    }

    private fun httpGet(path: String, token: SignedJWT): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun httpPost(path: String, body: String, token: SignedJWT): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
