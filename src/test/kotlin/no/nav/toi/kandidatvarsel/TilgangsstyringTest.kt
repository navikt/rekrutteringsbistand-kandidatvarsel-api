package no.nav.toi.kandidatvarsel

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.util.*

private const val kandidatsokPort = 10000
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SystemStubsExtension::class)
@WireMockTest(httpPort = kandidatsokPort)
class TilgangsstyringTest {
    private val app = LocalApp()
    @BeforeEach fun beforeEach() = app.prepare()
    @AfterAll fun afterAll() = app.close()

    @SystemStub
    private val variables = EnvironmentVariables(
        "AZURE_APP_CLIENT_ID", "1",
        "AZURE_OPENID_CONFIG_ISSUER", issuer,
        "AZURE_OPENID_CONFIG_ISSUER", issuer,
        "AZURE_OPENID_CONFIG_JWKS_URI", jwksUri,
        "AUTHORIZED_PARTY_NAMES", "party",
        "KANDIDATSOK_API_URL", "http://localhost:$kandidatsokPort",
        "AD_GROUP_REKBIS_UTVIKLER", UUID.randomUUID().toString(),
        "AD_GROUP_REKBIS_ARBEIDSGIVERRETTET", UUID.randomUUID().toString(),
        "AD_GROUP_REKBIS_JOBBSOKERRETTET", UUID.randomUUID().toString()
    )

    @Test
    fun `tilganger ved ingen roller`() {
        assertTilganger(
            token = app.userToken(groups = listOf()),
            tilgangOpprettVarsel = false,
            tilgangListVarslerPåStilling = false,
            tilgangListVarslerPåFnr = false,
            tilgangBackfill = false,
        )
    }

    @ParameterizedTest(name = "harTilgang er {0}")
    @ValueSource(booleans = [true, false])
    fun `tilganger for rekbis utvikler`(harBrukertilgang: Boolean, wmRuntimeInfo: WireMockRuntimeInfo) {
        mockKandidatsokApi(harBrukertilgang, wmRuntimeInfo)
        assertTilganger(
            token = app.userToken(groups = listOf(rekbisUtvikler)),
            tilgangOpprettVarsel = true,
            tilgangListVarslerPåStilling = true,
            tilgangListVarslerPåFnr = true,
            tilgangBackfill = false,
        )
    }

    @ParameterizedTest(name = "harTilgang er {0}")
    @ValueSource(booleans = [true, false])
    fun `tilganger for rekbis jobbsøkerrettet`(harBrukertilgang: Boolean, wmRuntimeInfo: WireMockRuntimeInfo) {
        mockKandidatsokApi(harBrukertilgang, wmRuntimeInfo)
        assertTilganger(
            token = app.userToken(groups = listOf(rekbisJobbsøkerrettet)),
            tilgangOpprettVarsel = false,
            tilgangListVarslerPåStilling = false,
            tilgangListVarslerPåFnr = harBrukertilgang,
            tilgangBackfill = false,
        )
    }

    @ParameterizedTest(name = "harTilgang er {0}")
    @ValueSource(booleans = [true, false])
    fun `tilganger for rekbis arbeidsgiverrettet`(harBrukertilgang: Boolean, wmRuntimeInfo: WireMockRuntimeInfo) {
        mockKandidatsokApi(harBrukertilgang, wmRuntimeInfo)
        assertTilganger(
            token = app.userToken(groups = listOf(rekbisArbeidsgiverrettet)),
            tilgangOpprettVarsel = true,
            tilgangListVarslerPåStilling = true,
            tilgangListVarslerPåFnr = true,
            tilgangBackfill = false,
        )

        assertTilganger(
            token = app.userToken(groups = listOf(rekbisArbeidsgiverrettet, rekbisJobbsøkerrettet)),
            tilgangOpprettVarsel = true,
            tilgangListVarslerPåStilling = true,
            tilgangListVarslerPåFnr = true,
            tilgangBackfill = false,
        )
    }

    @Test
    fun `tilganger for maskintoken`() {
        assertTilganger(
            token = app.maskinToken(),
            tilgangOpprettVarsel = false,
            tilgangListVarslerPåStilling = false,
            tilgangListVarslerPåFnr = false,
            tilgangBackfill = true,
        )
    }


    private fun assertTilganger(
        token: SignedJWT,
        tilgangOpprettVarsel: Boolean,
        tilgangListVarslerPåStilling: Boolean,
        tilgangListVarslerPåFnr: Boolean,
        tilgangBackfill: Boolean,
    ) {
        app.post("/api/varsler/stilling/1")
            .token(token)
            .body("""{"fnr": ["1"],"mal": "VURDERT_SOM_AKTUELL"}""")
            .response()
            .also { (_, response, _) ->
                assertEquals(if (tilgangOpprettVarsel) 201 else 403, response.statusCode)
            }

        app.get("/api/varsler/stilling/1")
            .token(token)
            .response()
            .also { (_, response, _) ->
                assertEquals(if (tilgangListVarslerPåStilling) 200 else 403, response.statusCode)
            }

        app.post("/api/varsler/query")
            .token(token)
            .body("""{"fnr": "1"}""")
            .response()
            .also { (_, response, _) ->
                assertEquals(if (tilgangListVarslerPåFnr) 200 else 403, response.statusCode)
            }

        app.post("/api/backfill")
            .token(token)
            .body("""
                [{
                    "frontendId": "0",
                    "opprettet": "2023-01-01T01:01:01",
                    "stillingId": "1",
                    "melding": "En melding",
                    "fnr": "11223388990",
                    "status": "FEIL",
                    "navIdent": "Z123456"
                }]
            """)
            .response()
            .also { (_, response, _) ->
                assertEquals(if (tilgangBackfill) 201 else 403, response.statusCode)
            }
    }

    private fun mockKandidatsokApi(harBrukertilgang: Boolean, wmRuntimeInfo: WireMockRuntimeInfo) {
        if(harBrukertilgang)
            wmRuntimeInfo.brukertilgangOk()
        else
            wmRuntimeInfo.brukertilgangForbidden()
    }
}