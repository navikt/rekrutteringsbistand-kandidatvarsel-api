package no.nav.toi.kandidatvarsel

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VarslerEndepunktTest {
    private val app = LocalApp()
    private val httpClient = HttpClient.newBuilder().build()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun beforeEach() = app.prepare()

    @AfterAll
    fun afterAll() = app.close()

    private val navident = "Z999999"
    private val token = app.userToken(navIdent = navident)
    private val fnr1 = "11111111111"
    private val fnr2 = "22222222222"

    @Test
    fun `GET stilling endepunkt returnerer varsler for stillingId`() {
        val stillingId = UUID.randomUUID().toString()

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
    fun `GET stilling endepunkt returnerer kun stilling-maler`() {
        val stillingId = UUID.randomUUID().toString()

        app.dataSource.transaction { tx ->
            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.VurdertSomAktuell,
                avsenderReferanseId = stillingId,
                mottakerFnr = fnr1,
                avsenderNavident = navident
            ).insert(tx)

            no.nav.toi.kandidatvarsel.minside.MinsideVarsel.create(
                mal = no.nav.toi.kandidatvarsel.minside.KandidatInvitertTreff,
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
    fun `GET stilling endepunkt returnerer tomt array når ingen varsler finnes`() {
        val stillingId = UUID.randomUUID().toString()

        val response = httpGet("/api/varsler/stilling/$stillingId", token)

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
