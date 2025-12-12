package no.nav.toi.kandidatvarsel

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.junit5.WireMockTest
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


private const val kandidatsokPort = 10003

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SystemStubsExtension::class)
@WireMockTest(httpPort = kandidatsokPort)
class MeldingsmalApiTest {
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

    @Test
    fun `GET meldingsmal endepunkt returnerer alle meldingsmaler uten auth`() {
        // Test uten token (endepunktet er UNPROTECTED)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}/api/meldingsmal"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, response.statusCode())
        
        val meldingsmal: Map<String, Any?> = objectMapper.readValue(response.body())
        
        // Verifiser at alle maler er med
        assertTrue(meldingsmal.containsKey("vurdertSomAktuell"))
        assertTrue(meldingsmal.containsKey("passendeStilling"))
        assertTrue(meldingsmal.containsKey("passendeJobbarrangement"))
        
        // Verifiser struktur for en av malene
        val vurdertSomAktuell = meldingsmal["vurdertSomAktuell"] as Map<*, *>
        assertTrue(vurdertSomAktuell.containsKey("smsTekst"))
        assertTrue(vurdertSomAktuell.containsKey("epostTittel"))
        assertTrue(vurdertSomAktuell.containsKey("epostHtmlBody"))
    }

    @Test
    fun `GET meldingsmal returnerer korrekt struktur for vurdertSomAktuell`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}/api/meldingsmal"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, response.statusCode())
        
        val meldingsmal: Map<String, Any?> = objectMapper.readValue(response.body())
        val vurdertSomAktuell = meldingsmal["vurdertSomAktuell"] as Map<*, *>
        
        // Verifiser at feltene ikke er tomme
        assertTrue((vurdertSomAktuell["smsTekst"] as String).isNotEmpty())
        assertTrue((vurdertSomAktuell["epostTittel"] as String).isNotEmpty())
        assertTrue((vurdertSomAktuell["epostHtmlBody"] as String).isNotEmpty())
    }

    @Test
    fun `GET meldingsmal returnerer korrekt struktur for passendeStilling`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}/api/meldingsmal"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, response.statusCode())
        
        val meldingsmal: Map<String, Any?> = objectMapper.readValue(response.body())
        val passendeStilling = meldingsmal["passendeStilling"] as Map<*, *>
        
        // Verifiser at feltene ikke er tomme
        assertTrue((passendeStilling["smsTekst"] as String).isNotEmpty())
        assertTrue((passendeStilling["epostTittel"] as String).isNotEmpty())
        assertTrue((passendeStilling["epostHtmlBody"] as String).isNotEmpty())
    }

    @Test
    fun `GET meldingsmal returnerer korrekt struktur for passendeJobbarrangement`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}/api/meldingsmal"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, response.statusCode())
        
        val meldingsmal: Map<String, Any?> = objectMapper.readValue(response.body())
        val passendeJobbarrangement = meldingsmal["passendeJobbarrangement"] as Map<*, *>
        
        // Verifiser at feltene ikke er tomme
        assertTrue((passendeJobbarrangement["smsTekst"] as String).isNotEmpty())
        assertTrue((passendeJobbarrangement["epostTittel"] as String).isNotEmpty())
        assertTrue((passendeJobbarrangement["epostHtmlBody"] as String).isNotEmpty())
    }

    @Test
    fun `GET meldingsmal-stilling endepunkt returnerer alle stillingsmaler uten auth`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}/api/meldingsmal/stilling"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, response.statusCode())
        
        val meldingsmal: Map<String, Any?> = objectMapper.readValue(response.body())
        
        // Verifiser at alle stillingsmaler er med
        assertTrue(meldingsmal.containsKey("vurdertSomAktuell"))
        assertTrue(meldingsmal.containsKey("passendeStilling"))
        assertTrue(meldingsmal.containsKey("passendeJobbarrangement"))
        
        // Verifiser struktur for en av malene
        val vurdertSomAktuell = meldingsmal["vurdertSomAktuell"] as Map<*, *>
        assertTrue(vurdertSomAktuell.containsKey("smsTekst"))
        assertTrue(vurdertSomAktuell.containsKey("epostTittel"))
        assertTrue(vurdertSomAktuell.containsKey("epostHtmlBody"))
    }

    @Test
    fun `GET meldingsmal-stilling returnerer samme struktur som meldingsmal`() {
        val request1 = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}/api/meldingsmal"))
            .GET()
            .build()
        val response1 = httpClient.send(request1, HttpResponse.BodyHandlers.ofString())
        
        val request2 = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}/api/meldingsmal/stilling"))
            .GET()
            .build()
        val response2 = httpClient.send(request2, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, response1.statusCode())
        assertEquals(200, response2.statusCode())
        
        // Begge skal returnere samme data
        assertEquals(response1.body(), response2.body())
    }

    @Test
    fun `GET meldingsmal-rekrutteringstreff endepunkt returnerer alle rekrutteringstreffmaler uten auth`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}/api/meldingsmal/rekrutteringstreff"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, response.statusCode())
        
        val meldingsmal: Map<String, Any?> = objectMapper.readValue(response.body())
        
        // Verifiser at alle rekrutteringstreffmaler er med
        assertTrue(meldingsmal.containsKey("kandidatInvitertTreff"))
        assertTrue(meldingsmal.containsKey("kandidatInvitertTreffEndret"))
        
        // Verifiser struktur for en av malene
        val kandidatInvitertTreff = meldingsmal["kandidatInvitertTreff"] as Map<*, *>
        assertTrue(kandidatInvitertTreff.containsKey("smsTekst"))
        assertTrue(kandidatInvitertTreff.containsKey("epostTittel"))
        assertTrue(kandidatInvitertTreff.containsKey("epostHtmlBody"))
    }

    @Test
    fun `GET meldingsmal-rekrutteringstreff returnerer korrekt struktur for kandidatInvitertTreff`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}/api/meldingsmal/rekrutteringstreff"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, response.statusCode())
        
        val meldingsmal: Map<String, Any?> = objectMapper.readValue(response.body())
        val kandidatInvitertTreff = meldingsmal["kandidatInvitertTreff"] as Map<*, *>
        
        // Verifiser at feltene ikke er tomme
        assertTrue((kandidatInvitertTreff["smsTekst"] as String).isNotEmpty())
        assertTrue((kandidatInvitertTreff["epostTittel"] as String).isNotEmpty())
        assertTrue((kandidatInvitertTreff["epostHtmlBody"] as String).isNotEmpty())
    }

    @Test
    fun `GET meldingsmal-rekrutteringstreff returnerer korrekt struktur for kandidatInvitertTreffEndret`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${app.javalinPort()}/api/meldingsmal/rekrutteringstreff"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, response.statusCode())
        
        val meldingsmal: Map<String, Any?> = objectMapper.readValue(response.body())
        val kandidatInvitertTreffEndret = meldingsmal["kandidatInvitertTreffEndret"] as Map<*, *>
        
        // Verifiser at feltene ikke er tomme
        assertTrue((kandidatInvitertTreffEndret["smsTekst"] as String).isNotEmpty())
        assertTrue((kandidatInvitertTreffEndret["epostTittel"] as String).isNotEmpty())
        assertTrue((kandidatInvitertTreffEndret["epostHtmlBody"] as String).isNotEmpty())
        
        // Verifiser placeholder
        assertEquals("{{ENDRINGER}}", kandidatInvitertTreffEndret["placeholder"])
        assertTrue((kandidatInvitertTreffEndret["smsTekst"] as String).contains("{{ENDRINGER}}"))
        
        // Verifiser endringsFelt
        @Suppress("UNCHECKED_CAST")
        val endringsFelt = kandidatInvitertTreffEndret["endringsFelt"] as List<Map<String, String>>
        assertEquals(5, endringsFelt.size)
        val koder = endringsFelt.map { it["kode"] }
        assertTrue(koder.contains("NAVN"))
        assertTrue(koder.contains("TIDSPUNKT"))
        assertTrue(koder.contains("SVARFRIST"))
        assertTrue(koder.contains("STED"))
        assertTrue(koder.contains("INTRODUKSJON"))
    }

    @Test
    fun `GET meldingsmal returnerer korrekte meldingstekster for alle stillingsmaler`() {
        val token = app.userToken(navIdent = "Z1")
        
        app.getMeldingsmal(token).also { meldingsmal ->
            assertEquals("Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav", 
                meldingsmal.vurdertSomAktuell.smsTekst)
            assertEquals("Stilling som kan passe for deg?", 
                meldingsmal.vurdertSomAktuell.epostTittel)
            assertEquals(no.nav.toi.kandidatvarsel.minside.Maler.epostHtmlBodyTemplate(
                "Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen."),
                meldingsmal.vurdertSomAktuell.epostHtmlBody)
            
            assertEquals("Hei! Vi har funnet en stilling som kan passe deg. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav", 
                meldingsmal.passendeStilling.smsTekst)
            assertEquals("Stilling som kan passe for deg?", 
                meldingsmal.passendeStilling.epostTittel)
            assertEquals(no.nav.toi.kandidatvarsel.minside.Maler.epostHtmlBodyTemplate(
                "Vi har funnet en stilling som kanskje kan passe for deg. Logg inn på Nav for å se stillingen."),
                meldingsmal.passendeStilling.epostHtmlBody)
            
            assertEquals("Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet. Vennlig hilsen Nav", 
                meldingsmal.passendeJobbarrangement.smsTekst)
            assertEquals("Jobbarrangement", 
                meldingsmal.passendeJobbarrangement.epostTittel)
            assertEquals(no.nav.toi.kandidatvarsel.minside.Maler.epostHtmlBodyTemplate(
                "Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet."),
                meldingsmal.passendeJobbarrangement.epostHtmlBody)
        }
    }

    @Test
    fun `GET meldingsmal-stilling returnerer korrekte meldingstekster for alle stillingsmaler`() {
        val token = app.userToken(navIdent = "Z1")
        
        app.getStillingMeldingsmal(token).also { meldingsmal ->
            assertEquals("Hei! Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav", 
                meldingsmal.vurdertSomAktuell.smsTekst)
            assertEquals("Stilling som kan passe for deg?", 
                meldingsmal.vurdertSomAktuell.epostTittel)
            assertEquals(no.nav.toi.kandidatvarsel.minside.Maler.epostHtmlBodyTemplate(
                "Vi har vurdert at kompetansen din kan passe til en stilling. Logg inn på Nav for å se stillingen."),
                meldingsmal.vurdertSomAktuell.epostHtmlBody)
            
            assertEquals("Hei! Vi har funnet en stilling som kan passe deg. Logg inn på Nav for å se stillingen. Vennlig hilsen Nav", 
                meldingsmal.passendeStilling.smsTekst)
            assertEquals("Stilling som kan passe for deg?", 
                meldingsmal.passendeStilling.epostTittel)
            assertEquals(no.nav.toi.kandidatvarsel.minside.Maler.epostHtmlBodyTemplate(
                "Vi har funnet en stilling som kanskje kan passe for deg. Logg inn på Nav for å se stillingen."),
                meldingsmal.passendeStilling.epostHtmlBody)
            
            assertEquals("Hei! Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet. Vennlig hilsen Nav", 
                meldingsmal.passendeJobbarrangement.smsTekst)
            assertEquals("Jobbarrangement", 
                meldingsmal.passendeJobbarrangement.epostTittel)
            assertEquals(no.nav.toi.kandidatvarsel.minside.Maler.epostHtmlBodyTemplate(
                "Vi har funnet et jobbarrangement som kanskje passer for deg. Logg inn på Nav for å se arrangementet."),
                meldingsmal.passendeJobbarrangement.epostHtmlBody)
        }
    }

    @Test
    fun `GET meldingsmal-rekrutteringstreff returnerer korrekte meldingstekster for alle rekrutteringstreffmaler`() {
        val token = app.userToken(navIdent = "Z1")
        
        app.getRekrutteringstreffMeldingsmal(token).also { meldingsmal ->
            assertEquals("Hei! Du er invitert til et treff der du kan møte arbeidsgivere. Logg inn på Nav for å melde deg på. Vennlig hilsen Nav", 
                meldingsmal.kandidatInvitertTreff.smsTekst)
            assertEquals("Invitasjon til å treffe arbeidsgivere", 
                meldingsmal.kandidatInvitertTreff.epostTittel)
            assertEquals("""
                <!DOCTYPE html><html><head><title>Melding</title></head><body><p>Hei! Du er invitert til et treff der du kan møte arbeidsgivere. Logg inn på Nav for å melde deg på.</p><p>Vennlig hilsen</p><p>Nav</p></body></html>
                """.trimIndent(),
                meldingsmal.kandidatInvitertTreff.epostHtmlBody)
            
            // Verifiser at KANDIDAT_INVITERT_TREFF_ENDRET har placeholder
            assertTrue(meldingsmal.kandidatInvitertTreffEndret.smsTekst.contains("{{ENDRINGER}}"))
            assertEquals("Endringer på treff du er invitert til", 
                meldingsmal.kandidatInvitertTreffEndret.epostTittel)
            assertTrue(meldingsmal.kandidatInvitertTreffEndret.epostHtmlBody.contains("{{ENDRINGER}}"))
            assertEquals("{{ENDRINGER}}", meldingsmal.kandidatInvitertTreffEndret.placeholder)
            assertEquals(5, meldingsmal.kandidatInvitertTreffEndret.endringsFelt.size)
        }
    }
}
