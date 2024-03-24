package no.nav.toi.kandidatvarsel

import org.junit.jupiter.api.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KubernetesHealthTest {
    private val app = LocalApp()
    @BeforeEach fun beforeEach() = app.prepare()
    @AfterAll fun afterAll() = app.close()

    private val timeout = 10.seconds
    private val retryInterval = 100.milliseconds
    private val attemptLimit = (timeout / retryInterval).toInt()

    @Test
    fun aliveDuringFlywayMigration() {
        app.migrateResult.set(null)

        app.get("/internal/alive").response().also { (_, response, _) ->
            Assertions.assertEquals(200, response.statusCode)
        }
    }

    @Test
    fun aliveAfterSuccessfulFlywayMigration() {
        app.migrateResult.get().success = true
        app.get("/internal/alive").response().also { (_, response, _) ->
            Assertions.assertEquals(200, response.statusCode)
        }
    }

    @Test
    fun notAliveAfterFailingFlywayMigration() {
        app.migrateResult.get().success = false
        app.get("/internal/alive").response().also { (_, response, _) ->
            Assertions.assertEquals(503, response.statusCode )
        }
    }

    @Test
    fun notReadyDuringFlywayMigration() {
        app.migrateResult.set(null)

        app.get("/internal/ready").response().also { (_, response, _) ->
            Assertions.assertEquals(503, response.statusCode)
        }
    }

    @Test
    fun readyAfterSuccessfulFlywayMigration() {
        app.migrateResult.get().success = true
        app.get("/internal/ready").response().also { (_, response, _) ->
            Assertions.assertEquals(200, response.statusCode)
        }
    }

    @Test
    fun notReadyAfterFailingFlywayMigration() {
        app.migrateResult.get().success = false
        app.get("/internal/ready").response().also { (_, response, _) ->
            Assertions.assertEquals(503, response.statusCode )
        }
    }

    @Test
    fun eventuallyReady() {
        var statusCode = 0

        for (attempt in 0 .. attemptLimit) {
            statusCode = app.get("/internal/ready").response().second.statusCode
            if (statusCode == 200) break
            Thread.sleep(retryInterval.inWholeMilliseconds)
        }

        Assertions.assertEquals(statusCode, 200)
    }
}