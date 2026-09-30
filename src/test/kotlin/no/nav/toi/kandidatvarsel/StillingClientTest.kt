package no.nav.toi.kandidatvarsel

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@WireMockTest
class StillingClientTest {
    private val stillingId = UUID.fromString("0199a0f0-0000-7000-8000-000000000002")
    private val stillingPath = "/rekrutteringsbistand/ekstern/api/v1/stilling/$stillingId"

    @BeforeEach
    fun stubToken() {
        stubFor(
            post(urlEqualTo("/token")).willReturn(
                aResponse().withHeader("Content-Type", "application/json")
                    .withBody("""{"access_token":"stilling-token","expires_in":3600}""")
            )
        )
    }

    private fun klient(wm: WireMockRuntimeInfo, baseUrl: String = wm.httpBaseUrl) = StillingClientImpl(
        AzureTokenClient("${wm.httpBaseUrl}/token", "klient-id", "hemmelig", "scope"),
        baseUrl,
    )

    private fun stubStilling(status: Int = 200, body: String = "") = stubFor(
        get(urlEqualTo(stillingPath)).willReturn(
            aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)
        )
    )

    @Test
    fun `henter stilling med token og ignorerer ukjente felt`(wm: WireMockRuntimeInfo) {
        stubStilling(body = """{"title":"Kokk","businessName":"Testbedrift AS","uuid":"$stillingId","status":"ACTIVE"}""")

        val stilling = klient(wm).getStilling(stillingId)

        assertThat(stilling).isEqualTo(Stilling(title = "Kokk", businessName = "Testbedrift AS"))
        verify(getRequestedFor(urlEqualTo(stillingPath)).withHeader("Authorization", equalTo("Bearer stilling-token")))
    }

    @Test
    fun `returnerer null når stilling-api svarer 404`(wm: WireMockRuntimeInfo) {
        stubStilling(status = 404)
        assertThat(klient(wm).getStilling(stillingId)).isNull()
    }

    @Test
    fun `returnerer null når stilling-api svarer 500`(wm: WireMockRuntimeInfo) {
        stubStilling(status = 500)
        assertThat(klient(wm).getStilling(stillingId)).isNull()
    }

    @Test
    fun `returnerer null når svaret ikke kan leses`(wm: WireMockRuntimeInfo) {
        stubStilling(body = """{"ikke":"en stilling"}""")
        assertThat(klient(wm).getStilling(stillingId)).isNull()
    }

    @Test
    fun `returnerer null og beholder interrupt-flagget når tråden avbrytes`(wm: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo(stillingPath)).willReturn(aResponse().withFixedDelay(2000)))
        val klient = klient(wm).also { it.getStilling(UUID.randomUUID()) } // henter token før avbruddet

        val (resultat, fortsattAvbrutt) = kjørAvbrutt { klient.getStilling(stillingId) }

        assertThat(fortsattAvbrutt).isTrue
        assertThat(resultat.getOrThrow()).isNull()
    }

    @Test
    fun `returnerer null når stilling-api ikke svarer`(wm: WireMockRuntimeInfo) {
        assertThat(klient(wm, baseUrl = "http://localhost:${ledigPort()}").getStilling(stillingId)).isNull()
    }
}
