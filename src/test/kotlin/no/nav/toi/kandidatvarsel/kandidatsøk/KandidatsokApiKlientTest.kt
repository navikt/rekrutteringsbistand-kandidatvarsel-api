package no.nav.toi.kandidatvarsel.kandidatsøk

import auth.obo.KandidatsokApiKlient
import auth.obo.OnBehalfOfTokenClient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import io.mockk.every
import io.mockk.mockk
import no.nav.toi.kandidatvarsel.ledigPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@WireMockTest
class KandidatsokApiKlientTest {
    private val ctx = mockk<Context>(relaxed = true)
    private val oboTokenClient = mockk<OnBehalfOfTokenClient> {
        every { oboToken(ctx) } returns "obo-token"
    }

    private fun stubBrukertilgang(status: Int) = stubFor(
        post(urlEqualTo("/api/brukertilgang")).willReturn(aResponse().withStatus(status).withBody("{}"))
    )

    private fun verifiser(url: String) =
        KandidatsokApiKlient(oboTokenClient, url).verifiserKandidatTilgang(ctx, "Z000001", "12345678901")

    @Test
    fun `sender fødselsnummer med obo-token og godtar 200`(wm: WireMockRuntimeInfo) {
        stubBrukertilgang(200)

        assertThatCode { verifiser(wm.httpBaseUrl) }.doesNotThrowAnyException()

        verify(
            postRequestedFor(urlEqualTo("/api/brukertilgang"))
                .withHeader("Authorization", equalTo("Bearer obo-token"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("""{"fodselsnummer":"12345678901","aktorid":null,"kandidatnr":null}"""))
        )
    }

    @ParameterizedTest(name = "kandidatsøk svarer {0} gir {1}")
    @CsvSource("403,403", "404,404", "500,500", "401,500")
    fun `oversetter feil fra kandidatsøk`(kandidatsokStatus: Int, forventetStatus: Int, wm: WireMockRuntimeInfo) {
        stubBrukertilgang(kandidatsokStatus)

        val feil = catchThrowableOfType(HttpResponseException::class.java) { verifiser(wm.httpBaseUrl) }

        assertThat(feil.status).isEqualTo(forventetStatus)
    }

    @Test
    fun `gir 500 når kandidatsøk ikke svarer`() {
        val feil = catchThrowableOfType(HttpResponseException::class.java) { verifiser("http://localhost:${ledigPort()}") }

        assertThat(feil.status).isEqualTo(500)
    }
}
