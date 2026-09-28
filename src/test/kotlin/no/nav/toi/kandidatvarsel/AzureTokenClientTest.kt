package no.nav.toi.kandidatvarsel

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URLDecoder

@WireMockTest
class AzureTokenClientTest {

    private fun klient(tokenEndpoint: String) = AzureTokenClient(
        tokenEndpoint = tokenEndpoint,
        clientId = "klient-id",
        clientSecret = "hemmelig&=+ø",
        scope = "api://dev-gcp.toi.stilling/.default",
    )

    private fun stubToken(token: String = "token-1", expiresIn: Long = 3600, status: Int = 200) = stubFor(
        post(urlEqualTo("/token")).willReturn(
            aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"token_type":"Bearer","access_token":"$token","expires_in":$expiresIn,"ext_expires_in":$expiresIn}""")
        )
    )

    @Test
    fun `henter token med client credentials som form-parametre`(wm: WireMockRuntimeInfo) {
        stubToken()

        val token = klient("${wm.httpBaseUrl}/token").authToken()

        assertThat(token).isEqualTo("token-1")
        verify(
            postRequestedFor(urlEqualTo("/token"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
        )
        val body = wm.wireMock.find(postRequestedFor(urlEqualTo("/token"))).single().bodyAsString
        assertThat(formParametre(body)).isEqualTo(
            mapOf(
                "client_id" to "klient-id",
                "client_secret" to "hemmelig&=+ø",
                "grant_type" to "client_credentials",
                "scope" to "api://dev-gcp.toi.stilling/.default",
            )
        )
    }

    @Test
    fun `gjenbruker token til det nesten er utløpt`(wm: WireMockRuntimeInfo) {
        stubToken()
        val klient = klient("${wm.httpBaseUrl}/token")

        klient.authToken()
        klient.authToken()

        verify(1, postRequestedFor(urlEqualTo("/token")))
    }

    @Test
    fun `henter nytt token når det gamle er utløpt`(wm: WireMockRuntimeInfo) {
        stubToken(expiresIn = 10)
        val klient = klient("${wm.httpBaseUrl}/token")

        klient.authToken()
        klient.authToken()

        verify(2, postRequestedFor(urlEqualTo("/token")))
    }

    @Test
    fun `kaster RuntimeException når token-endepunktet svarer med feil`(wm: WireMockRuntimeInfo) {
        stubToken(status = 500)

        assertThatThrownBy { klient("${wm.httpBaseUrl}/token").authToken() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageStartingWith("Failed to get token")
    }

    @Test
    fun `kaster RuntimeException og beholder interrupt-flagget når tråden avbrytes`(wm: WireMockRuntimeInfo) {
        stubFor(post(urlEqualTo("/token")).willReturn(aResponse().withFixedDelay(2000)))

        val (resultat, fortsattAvbrutt) = kjørAvbrutt { klient("${wm.httpBaseUrl}/token").authToken() }

        assertThat(fortsattAvbrutt).isTrue
        assertThat(resultat.exceptionOrNull())
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageStartingWith("Failed to get token")
    }

    @Test
    fun `kaster RuntimeException når token-endepunktet ikke svarer`() {
        assertThatThrownBy { klient("http://localhost:${ledigPort()}/token").authToken() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageStartingWith("Failed to get token")
    }
}

internal fun formParametre(body: String): Map<String, String> =
    body.split("&").associate {
        val (k, v) = it.split("=", limit = 2)
        URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
    }

internal fun ledigPort(): Int = ServerSocket(0).use { it.localPort }

/** Kjører [block] på en avbrutt tråd og returnerer om interrupt-flagget fortsatt var satt etterpå. */
internal fun <T> kjørAvbrutt(block: () -> T): Pair<Result<T>, Boolean> {
    Thread.currentThread().interrupt()
    val resultat = runCatching(block)
    return resultat to Thread.interrupted()
}
