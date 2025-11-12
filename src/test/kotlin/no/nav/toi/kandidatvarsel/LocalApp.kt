package no.nav.toi.kandidatvarsel

import auth.obo.KandidatsokApiKlient
import auth.obo.OnBehalfOfTokenClient
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.KafkaRapid
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.nimbusds.jwt.SignedJWT
import io.mockk.every
import io.mockk.mockk
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.testcontainers.containers.PostgreSQLContainer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import java.util.concurrent.atomic.AtomicReference


const val rekbisJobbsøkerrettet = "0dba8374-bf36-4d89-bbba-662447d57b94"
const val rekbisArbeidsgiverrettet = "52bc2af7-38d1-468b-b68d-0f3a4de45af2"
const val rekbisUtvikler = "a1749d9a-52e0-4116-bb9f-935c38f6c74a"

private const val authPort = 18306
const val issuer = "http://localhost:$authPort/default"
const val tokenEndpoint = "$issuer/token"
const val jwksUri = "$issuer/jwks"

private val azureAdConfig = AzureAdConfig(
    issuers = listOf(
        Issuer(
            audience = "1",
            issuer = issuer,
            jwksUri = jwksUri,
        )
    ),
    rekbisArbeidsgiverrettet = UUID.fromString(rekbisArbeidsgiverrettet),
    rekbisJobbsøkerrettet = UUID.fromString(rekbisJobbsøkerrettet),
    rekbisUtvikler = UUID.fromString(rekbisUtvikler),
)

class LocalApp() {
    private val authServer = MockOAuth2Server().also {
        it.start(port = authPort)
    }

    private val postgres = PostgreSQLContainer("postgres:15-alpine").also {
        it.start()
    }

    private val databaseConfig = DatabaseConfig(
        hostname = postgres.host,
        port = postgres.firstMappedPort,
        database = postgres.databaseName,
        username = postgres.username,
        password = postgres.password,
    )

    val dataSource = databaseConfig.createDataSource()
        .also {
            while (!it.isReady()) {
                // Wait for the database to be ready
            }
        }

    val kandidatsokApiKlient = KandidatsokApiKlient(
        OnBehalfOfTokenClient(
            tokenEndpoint = tokenEndpoint,
            clientId = "client-id",
            clientSecret = "client",
            scope = "",
            issuernavn = issuer
        ),
        kandidatsokUrl = "http://localhost:10000"
    )

    private val flyway = Flyway.configure()
        .dataSource(dataSource)
        .cleanDisabled(false)
        .load()


    val migrateResult = AtomicReference<MigrateResult>()

    private val mockKafkaRapid = mockk<KafkaRapid>().also {
        every { it.isRunning() } returns true
    }

    private var javalin = startJavalin(azureAdConfig, dataSource, migrateResult, kandidatsokApiKlient, mockKafkaRapid, port = 0)
    private val httpClient = HttpClient.newBuilder().build()
    private val objectMapper = jacksonObjectMapper()

    fun prepare() {
        flyway.clean()
        migrateResult.set(dataSource.migrate())
    }

    fun close() {
        javalin.stop()
        dataSource.close()
        postgres.close()
        authServer.shutdown()
    }

    fun javalinPort() = javalin.port()

    private fun issueToken(
        issuerId: String,
        audience: String,
        claims: Map<String, Any>
    ) =
        authServer.issueToken(
            issuerId = issuerId,
            audience = audience,
            subject = "subject",
            claims = claims,
        )

    fun userToken(
        issuerId: String = "http://localhost:$authPort/default",
        audience: String = "1",
        navIdent: String = "A000001",
        groups: List<String> = listOf(rekbisUtvikler),
        claims: Map<String, Any> = mapOf("NAVident" to navIdent, "groups" to groups),
    ) = issueToken(
        issuerId = issuerId,
        audience = audience,
        claims = claims
    )

    fun postVarselStilling(
        stillingId: String,
        fnr: List<String>,
        mal: String,
        token: SignedJWT,
    ) {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "fnr" to fnr,
                "mal" to mal
            )
        )
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${javalin.port()}/api/varsler/stilling/$stillingId"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(201, response.statusCode())
    }

    fun getVarselStilling(
        stillingId: String,
        token: SignedJWT,
    ): Map<String, JsonNode> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${javalin.port()}/api/varsler/stilling/$stillingId"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        val arrayNode = objectMapper.readValue<ArrayNode>(response.body())
        return arrayNode.toList().associateBy { it["mottakerFnr"].asText() }
    }

    fun getVarselRekrutteringstreff(
        rekrutteringstreffId: String,
        token: SignedJWT,
    ): Map<String, JsonNode> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${javalin.port()}/api/varsler/rekrutteringstreff/$rekrutteringstreffId"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        val arrayNode = objectMapper.readValue<ArrayNode>(response.body())
        return arrayNode.toList().associateBy { it["mottakerFnr"].asText() }
    }

    fun getVarselFnr(fnr: String, token: SignedJWT): Map<String, JsonNode> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${javalin.port()}/api/varsler/query"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"fnr":"$fnr"}"""))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        val arrayNode = objectMapper.readValue<ArrayNode>(response.body())
        return arrayNode.toList().associateBy { it["stillingId"].asText() }
    }
    // TODO: Kan fjernes når vi hhar tatt i bruk stillingsmeldingsmal
    fun getMeldingsmal(token: SignedJWT): Meldingsmal {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${javalin.port()}/api/meldingsmal"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        return objectMapper.readValue<Meldingsmal>(response.body())
    }

    fun getStillingMeldingsmal(token: SignedJWT): StillingMeldingsmal {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${javalin.port()}/api/meldingsmal/stilling"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        return objectMapper.readValue<StillingMeldingsmal>(response.body())
    }

    fun getRekrutteringstreffMeldingsmal(token: SignedJWT): RekrutteringstreffMeldingsmal {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${javalin.port()}/api/meldingsmal/rekrutteringstreff"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        return objectMapper.readValue<RekrutteringstreffMeldingsmal>(response.body())
    }

    // Helper methods for tests that need more control over HTTP calls
    fun post(path: String) = HttpRequestBuilder(
        method = "POST",
        uri = URI.create("http://localhost:${javalin.port()}$path"),
        httpClient = httpClient
    )

    fun get(path: String) = HttpRequestBuilder(
        method = "GET",
        uri = URI.create("http://localhost:${javalin.port()}$path"),
        httpClient = httpClient
    )
}

class HttpRequestBuilder(
    private val method: String,
    private val uri: URI,
    private val httpClient: HttpClient
) {
    private var authToken: String? = null
    private var bodyContent: String? = null

    fun token(token: SignedJWT): HttpRequestBuilder {
        authToken = "Bearer ${token.serialize()}"
        return this
    }

    fun body(body: String): HttpRequestBuilder {
        bodyContent = body
        return this
    }

    fun response(): Triple<HttpRequest, HttpResponse<String>, Unit> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(uri)
        
        authToken?.let { requestBuilder.header("Authorization", it) }
        
        when (method) {
            "POST" -> {
                requestBuilder.header("Content-Type", "application/json")
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyContent ?: ""))
            }
            "GET" -> requestBuilder.GET()
        }
        
        val request = requestBuilder.build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return Triple(request, response, Unit)
    }
}

fun WireMockRuntimeInfo.brukertilgangOk() {
    wireMock.register(
        WireMock.post("/api/brukertilgang")
            .willReturn(
                WireMock.ok()
            )
    )
}

fun WireMockRuntimeInfo.brukertilgangForbidden() {
    wireMock.register(
        WireMock.post("/api/brukertilgang")
            .willReturn(
                WireMock.forbidden()
            )
    )
}