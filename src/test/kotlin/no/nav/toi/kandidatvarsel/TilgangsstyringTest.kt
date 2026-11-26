package no.nav.toi.kandidatvarsel

import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilgangsstyringTest {
    private val app = LocalApp()
    @BeforeEach fun beforeEach() = app.prepare()
    @AfterAll fun afterAll() = app.close()

    @Test
    fun `tilganger ved ingen roller`() {
        assertTilganger(
            token = app.userToken(groups = listOf()),
            tilgangOpprettVarsel = false,
            tilgangListVarslerPåStilling = false,
        )
    }

    @Test
    fun `tilganger for rekbis utvikler`() {
        assertTilganger(
            token = app.userToken(groups = listOf(rekbisUtvikler)),
            tilgangOpprettVarsel = true,
            tilgangListVarslerPåStilling = true,
        )
    }

    @Test
    fun `tilganger for rekbis jobbsøkerrettet`() {
        assertTilganger(
            token = app.userToken(groups = listOf(rekbisJobbsøkerrettet)),
            tilgangOpprettVarsel = false,
            tilgangListVarslerPåStilling = false,
        )
    }

    @Test
    fun `tilganger for rekbis arbeidsgiverrettet`() {
        assertTilganger(
            token = app.userToken(groups = listOf(rekbisArbeidsgiverrettet)),
            tilgangOpprettVarsel = true,
            tilgangListVarslerPåStilling = true,
        )

        assertTilganger(
            token = app.userToken(groups = listOf(rekbisArbeidsgiverrettet, rekbisJobbsøkerrettet)),
            tilgangOpprettVarsel = true,
            tilgangListVarslerPåStilling = true,
        )
    }

    private fun assertTilganger(
        token: SignedJWT,
        tilgangOpprettVarsel: Boolean,
        tilgangListVarslerPåStilling: Boolean,
    ) {
        app.post("/api/varsler/stilling/1")
            .token(token)
            .body("""{"fnr": ["1"],"mal": "VURDERT_SOM_AKTUELL"}""")
            .response()
            .also { (_, response, _) ->
                assertEquals(if (tilgangOpprettVarsel) 201 else 403, response.statusCode())
            }

        app.get("/api/varsler/stilling/1")
            .token(token)
            .response()
            .also { (_, response, _) ->
                assertEquals(if (tilgangListVarslerPåStilling) 200 else 403, response.statusCode())
            }
    }
}
