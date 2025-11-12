package no.nav.toi.kandidatvarsel

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
class VarslerEndepunktTest {
    private val app = LocalApp()
    private val httpClient = HttpClient.newBuilder().build()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun beforeEach() = app.prepare()

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

    @Test
    fun `GET stilling endepunkt returnerer varsler for stillingId`() {
        val stillingId = UUID.randomUUID().toString()

        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.Mal.Companion.VurdertSomAktuell,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr1,
                avsenderNavident = navident
            ).insert(tx)

            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.Mal.Companion.PassendeStilling,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr2,
                avsenderNavident = navident
            ).insert(tx)
        }

        val response = httpGet("/api/varsler/stilling/$stillingId", token)

        assertEquals(200, response.statusCode())

        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(2, varsler.size)

        val mottakerFnrListe = varsler.map { it["mottakerFnr"] as String }
        assertTrue(mottakerFnrListe.contains(fnr1))
        assertTrue(mottakerFnrListe.contains(fnr2))

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

        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.Mal.Companion.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr1,
                avsenderNavident = navident
            ).insert(tx)

            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.Mal.Companion.InvitertTreffKandidatEndret,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr2,
                avsenderNavident = navident
            ).insert(tx)
        }

        val response = httpGet("/api/varsler/rekrutteringstreff/$rekrutteringstreffId", token)

        assertEquals(200, response.statusCode())

        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(2, varsler.size)

        val mottakerFnrListe = varsler.map { it["mottakerFnr"] as String }
        assertTrue(mottakerFnrListe.contains(fnr1))
        assertTrue(mottakerFnrListe.contains(fnr2))

        varsler.forEach { varsel ->
            assertEquals(rekrutteringstreffId, varsel["stillingId"] as String)
            assertEquals(navident, varsel["avsenderNavident"] as String)
            assertEquals("UNDER_UTSENDING", varsel["minsideStatus"] as String)
            assertEquals("UNDER_UTSENDING", varsel["eksternStatus"] as String)
        }
    }

    @Test
    fun `GET stilling endepunkt returnerer kun stilling-maler`() {
        val stillingId = UUID.randomUUID().toString()

        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.Mal.Companion.VurdertSomAktuell,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr1,
                avsenderNavident = navident
            ).insert(tx)

            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.Mal.Companion.KandidatInvitertTreff,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr2,
                avsenderNavident = navident
            ).insert(tx)
        }

        val response = httpGet("/api/varsler/stilling/$stillingId", token)

        assertEquals(200, response.statusCode())

        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(1, varsler.size)
        assertEquals(fnr1, varsler[0]["mottakerFnr"] as String)
    }

    @Test
    fun `GET rekrutteringstreff endepunkt returnerer kun rekrutteringstreff-maler`() {
        val rekrutteringstreffId = UUID.randomUUID().toString()

        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.Mal.Companion.KandidatInvitertTreff,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr1,
                avsenderNavident = navident
            ).insert(tx)

            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.Mal.Companion.VurdertSomAktuell,
                avsenderReferanseId = rekrutteringstreffId,
                mottakerFnr = fnr2,
                avsenderNavident = navident
            ).insert(tx)
        }

        val response = httpGet("/api/varsler/rekrutteringstreff/$rekrutteringstreffId", token)

        assertEquals(200, response.statusCode())

        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(1, varsler.size)
        assertEquals(fnr1, varsler[0]["mottakerFnr"] as String)
    }

    @Test
    fun `GET stilling endepunkt returnerer tomt array når ingen varsler finnes`() {
        val stillingId = UUID.randomUUID().toString()

        val response = httpGet("/api/varsler/stilling/$stillingId", token)

        assertEquals(200, response.statusCode())

        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(0, varsler.size)
    }

    @Test
    fun `GET rekrutteringstreff endepunkt returnerer tomt array når ingen varsler finnes`() {
        val rekrutteringstreffId = UUID.randomUUID().toString()

        val response = httpGet("/api/varsler/rekrutteringstreff/$rekrutteringstreffId", token)

        assertEquals(200, response.statusCode())

        val varsler: List<Map<String, Any?>> = objectMapper.readValue(response.body())
        assertEquals(0, varsler.size)
    }

    private fun httpGet(path: String, token: SignedJWT): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}