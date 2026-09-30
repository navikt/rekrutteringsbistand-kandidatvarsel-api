package no.nav.toi.kandidatvarsel.kandidatsøk

import auth.obo.OnBehalfOfTokenClient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.javalin.http.Context
import io.mockk.every
import io.mockk.mockk
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.kandidatvarsel.formParametre
import no.nav.toi.kandidatvarsel.ledigPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.util.UUID

@WireMockTest
@ExtendWith(SystemStubsExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OnBehalfOfTokenClientTest {
    private val authServer = MockOAuth2Server()
    private val issuerId = "obo-test"

    @BeforeAll
    fun startAuthServer() = authServer.start()

    @AfterAll
    fun stoppAuthServer() = authServer.shutdown()

    private val issuer get() = authServer.issuerUrl(issuerId).toString()

    @SystemStub
    private val variables = EnvironmentVariables()

    private fun medAzureEnv() {
        variables.set("AZURE_APP_CLIENT_ID", "1")
        variables.set("AZURE_OPENID_CONFIG_ISSUER", issuer)
        variables.set("AZURE_OPENID_CONFIG_JWKS_URI", authServer.jwksUrl(issuerId).toString())
        variables.set("AD_GROUP_REKBIS_UTVIKLER", UUID.randomUUID().toString())
        variables.set("AD_GROUP_REKBIS_ARBEIDSGIVERRETTET", UUID.randomUUID().toString())
        variables.set("AD_GROUP_REKBIS_JOBBSOKERRETTET", UUID.randomUUID().toString())
    }

    private fun ctxMedToken(token: String) = mockk<Context> {
        every { header("AUTHORIZATION") } returns "Bearer $token"
    }

    private fun klient(tokenEndpoint: String, issuernavn: String = issuer) = OnBehalfOfTokenClient(
        scope = "api://dev-gcp.toi.rekrutteringsbistand-kandidatsok-api/.default",
        tokenEndpoint = tokenEndpoint,
        clientId = "klient-id",
        clientSecret = "hemmelig&=+",
        issuernavn = issuernavn,
    )

    private fun stubToken(status: Int = 200) = stubFor(
        post(urlEqualTo("/token")).willReturn(
            aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                .withBody("""{"token_type":"Bearer","access_token":"obo-token","expires_in":3600}""")
        )
    )

    @Test
    fun `veksler innkommende token til obo-token`(wm: WireMockRuntimeInfo) {
        medAzureEnv()
        stubToken()
        val innkommende = authServer.issueToken(issuerId, audience = "1").serialize()

        val oboToken = klient("${wm.httpBaseUrl}/token").oboToken(ctxMedToken(innkommende))

        assertThat(oboToken).isEqualTo("obo-token")
        verify(
            postRequestedFor(urlEqualTo("/token"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
        )
        val body = wm.wireMock.find(postRequestedFor(urlEqualTo("/token"))).single().bodyAsString
        assertThat(formParametre(body)).isEqualTo(
            mapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "client_id" to "klient-id",
                "client_secret" to "hemmelig&=+",
                "assertion" to innkommende,
                "scope" to "api://dev-gcp.toi.rekrutteringsbistand-kandidatsok-api/.default",
                "requested_token_use" to "on_behalf_of",
            )
        )
    }

    @Test
    fun `kaster RuntimeException når token-endepunktet svarer med feil`(wm: WireMockRuntimeInfo) {
        medAzureEnv()
        stubToken(status = 400)
        val innkommende = authServer.issueToken(issuerId, audience = "1").serialize()

        assertThatThrownBy { klient("${wm.httpBaseUrl}/token").oboToken(ctxMedToken(innkommende)) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageStartingWith("Failed to get token")
    }

    @Test
    fun `kaster RuntimeException når token-endepunktet ikke svarer`() {
        medAzureEnv()
        val innkommende = authServer.issueToken(issuerId, audience = "1").serialize()

        assertThatThrownBy { klient("http://localhost:${ledigPort()}/token").oboToken(ctxMedToken(innkommende)) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageStartingWith("Failed to get token")
    }

    @Test
    fun `veksler ikke token fra annen issuer`(wm: WireMockRuntimeInfo) {
        medAzureEnv()
        stubToken()
        val innkommende = authServer.issueToken(issuerId, audience = "1").serialize()

        assertThatThrownBy { klient("${wm.httpBaseUrl}/token", issuernavn = "http://annen-issuer").oboToken(ctxMedToken(innkommende)) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("issuer")
        verify(0, postRequestedFor(urlEqualTo("/token")))
    }
}
