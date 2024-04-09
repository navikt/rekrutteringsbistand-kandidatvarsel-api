package no.nav.toi.kandidatvarsel

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackfillTest {
    private val app = LocalApp()
    @BeforeEach fun beforeEach() = app.prepare()
    @AfterAll fun afterAll() = app.close()

    @Test
    fun backfill() {
        val stillingId = "1234"

        app.post("/api/backfill")
            .token(app.maskinToken())
            .body("""
                [{
                    "frontendId": "0",
                    "opprettet": "2023-01-01T01:01:01",
                    "stillingId": "$stillingId",
                    "melding": "En melding",
                    "fnr": "11223388990",
                    "status": "UNDER_UTSENDING",
                    "navIdent": "Z123456"
                }]
            """.trimIndent())
            .response()
            .also { (_, response, _) ->
                assertEquals(201, response.statusCode)
            }

        app.getVarselStilling(stillingId, app.userToken()).also {
            assertEquals(1, it.size)
            val varsel = it["11223388990"]!!
            assertEquals("11223388990", varsel["mottakerFnr"].asText())
            assertEquals("2023-01-01T01:01:01", varsel["opprettet"].asText())
            assertEquals("0", varsel["id"].asText())
            assertEquals("IKKE_BESTILT", varsel["minsideStatus"].asText())
            assertEquals("UNDER_UTSENDING", varsel["eksternStatus"].asText())
            assertEquals("Z123456", varsel["avsenderNavident"].asText())
            assertTrue(varsel["eksternFeilmelding"].isNull)
            assertEquals(stillingId, varsel["stillingId"].asText())
        }

        app.post("/api/backfill")
            .token(app.maskinToken())
            .body("""
                [{
                    "frontendId": "0",
                    "opprettet": "2023-01-01T01:01:01",
                    "stillingId": "$stillingId",
                    "melding": "En melding",
                    "fnr": "11223388990",
                    "status": "SENDT",
                    "navIdent": "Z123456"
                }]
            """.trimIndent())
            .response()
            .also {(_, response, _) ->
                assertEquals(201, response.statusCode)
            }

        app.getVarselStilling(stillingId, app.userToken()).also {
            assertEquals(1, it.size)
            val varsel = it["11223388990"]!!
            assertEquals("11223388990", varsel["mottakerFnr"].asText())
            assertEquals("2023-01-01T01:01:01", varsel["opprettet"].asText())
            assertEquals("0", varsel["id"].asText())
            assertEquals("IKKE_BESTILT", varsel["minsideStatus"].asText())
            assertEquals("VELLYKKET_SMS", varsel["eksternStatus"].asText())
            assertEquals("Z123456", varsel["avsenderNavident"].asText())
            assertTrue(varsel["eksternFeilmelding"].isNull)
            assertEquals(stillingId, varsel["stillingId"].asText())
        }

        app.post("/api/backfill")
            .token(app.maskinToken())
            .body("""
                [{
                    "frontendId": "0",
                    "opprettet": "2023-01-01T01:01:01",
                    "stillingId": "$stillingId",
                    "melding": "En melding",
                    "fnr": "11223388990",
                    "status": "FEIL",
                    "navIdent": "Z123456"
                }]
            """.trimIndent())
            .response()
            .also {(_, response, _) ->
                assertEquals(201, response.statusCode)
            }

        app.getVarselStilling(stillingId, app.userToken()).also {
            assertEquals(1, it.size)
            val varsel = it["11223388990"]!!
            assertEquals("11223388990", varsel["mottakerFnr"].asText())
            assertEquals("2023-01-01T01:01:01", varsel["opprettet"].asText())
            assertEquals("0", varsel["id"].asText())
            assertEquals("IKKE_BESTILT", varsel["minsideStatus"].asText())
            assertEquals("FEIL", varsel["eksternStatus"].asText())
            assertEquals("Z123456", varsel["avsenderNavident"].asText())
            assertTrue(varsel["eksternFeilmelding"].isNull)
            assertEquals(stillingId, varsel["stillingId"].asText())
        }
    }
}