package no.nav.toi.kandidatvarsel

import auth.obo.KandidatsokApiKlient
import auth.obo.OnBehalfOfTokenClient
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.getOrNull
import com.github.kittinunf.result.onError
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.testcontainers.containers.PostgreSQLContainer
import java.util.*
import java.util.concurrent.atomic.AtomicReference


const val rekbisJobbsøkerrettet = "0dba8374-bf36-4d89-bbba-662447d57b94"
const val rekbisArbeidsgiverrettet = "52bc2af7-38d1-468b-b68d-0f3a4de45af2"
const val rekbisUtvikler = "a1749d9a-52e0-4116-bb9f-935c38f6c74a"
const val authorizedPartyName = "local:toi:rekrutteringsbistand-kandidatvarsel-api"

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
    authorizedPartyNames = listOf(authorizedPartyName),
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

    private var javalin = startJavalin(azureAdConfig, dataSource, migrateResult, kandidatsokApiKlient, port = 0)

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

    fun post(path: String) = Fuel.post("http://localhost:${javalin.port()}$path")

    fun get(path: String) = Fuel.get("http://localhost:${javalin.port()}$path")

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
        val (request, response, bodyOrError) = post("/api/varsler/stilling/$stillingId")
            .token(token)
            .objectBody(
                mapOf(
                    "fnr" to fnr,
                    "mal" to mal
                )
            )
            .response()
        bodyOrError.onError {
            throw RuntimeException("Request ${request.method} ${request.url} failed", it.exception)
        }
        assertEquals(201, response.statusCode)
    }

    fun getVarselStilling(
        stillingId: String,
        token: SignedJWT,
    ): Map<String, JsonNode> {
        val (_, response, body) = get("/api/varsler/stilling/$stillingId")
            .token(token)
            .responseObject<JsonNode>()

        assertEquals(200, response.statusCode)
        val arrayNode = body.getOrNull() as ArrayNode
        return arrayNode.toList().associateBy { it["mottakerFnr"].asText() }
    }

    fun getVarselFnr(fnr: String, token: SignedJWT): Map<String, JsonNode> {
        val (_, response, body) = post("/api/varsler/query")
            .token(token)
            .body("""{"fnr":"$fnr"}""")
            .responseObject<JsonNode>()

        assertEquals(200, response.statusCode)
        val arrayNode = body.getOrNull() as ArrayNode
        return arrayNode.toList().associateBy { it["stillingId"].asText() }
    }
}

fun Request.token(token: SignedJWT): Request =
    header("Authorization", "Bearer ${token.serialize()}")

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