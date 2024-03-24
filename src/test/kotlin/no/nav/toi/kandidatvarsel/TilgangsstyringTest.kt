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
            tilgangListVarslerPåFnr = false,
            tilgangBackfill = false,
        )
    }

    @Test
    fun `tilganger for modia generell`() {
        assertTilganger(
            token = app.userToken(groups = listOf(modiaGenerell)),
            tilgangOpprettVarsel = false,
            tilgangListVarslerPåStilling = false,
            tilgangListVarslerPåFnr = false,
            tilgangBackfill = false,
        )
    }

    @Test
    fun `tilganger for modia oppfølging`() {
        assertTilganger(
            token = app.userToken(groups = listOf(modiaOppfølging)),
            tilgangOpprettVarsel = false,
            tilgangListVarslerPåStilling = false,
            tilgangListVarslerPåFnr = false,
            tilgangBackfill = false,
        )
    }

    @Test
    fun `tilganger for rekbis utvikler`() {
        assertTilganger(
            token = app.userToken(groups = listOf(rekbisUtvikler)),
            tilgangOpprettVarsel = true,
            tilgangListVarslerPåStilling = true,
            tilgangListVarslerPåFnr = true,
            tilgangBackfill = false,
        )
    }

    @Test
    fun `tilganger for rekbis jobbsøkerrettet`() {
        assertTilganger(
            token = app.userToken(groups = listOf(rekbisJobbsøkerrettet)),
            tilgangOpprettVarsel = false,
            tilgangListVarslerPåStilling = false,
            tilgangListVarslerPåFnr = true,
            tilgangBackfill = false,
        )

        assertTilganger(
            token = app.userToken(groups = listOf(rekbisJobbsøkerrettet, modiaGenerell)),
            tilgangOpprettVarsel = false,
            tilgangListVarslerPåStilling = false,
            tilgangListVarslerPåFnr = true,
            tilgangBackfill = false,
        )
    }

    @Test
    fun `tilganger for rekbis arbeidsgiverrettet`() {
        assertTilganger(
            token = app.userToken(groups = listOf(rekbisArbeidsgiverrettet)),
            tilgangOpprettVarsel = true,
            tilgangListVarslerPåStilling = true,
            tilgangListVarslerPåFnr = true,
            tilgangBackfill = false,
        )

        assertTilganger(
            token = app.userToken(groups = listOf(rekbisArbeidsgiverrettet, modiaGenerell)),
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
                {
                    "frontendId": "0",
                    "opprettet": "2023-01-01T01:01:01",
                    "stillingId": "1",
                    "melding": "En melding",
                    "fnr": "11223388990",
                    "status": "FEIL",
                    "statusEndret": "2024-01-02T01:01:01Z",
                    "navIdent": "Z123456"
                }
            """)
            .response()
            .also { (_, response, _) ->
                assertEquals(if (tilgangBackfill) 201 else 403, response.statusCode)
            }
    }
}