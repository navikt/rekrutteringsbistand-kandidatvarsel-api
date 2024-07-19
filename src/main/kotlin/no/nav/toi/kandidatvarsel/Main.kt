package no.nav.toi.kandidatvarsel

import auth.obo.KandidatsokApiKlient
import auth.obo.OnBehalfOfTokenClient
import no.nav.toi.kandidatvarsel.minside.bestillVarsel
import no.nav.toi.kandidatvarsel.minside.sjekkVarselOppdateringer
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.System.getenv
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("no.nav.toi.kandidatvarsel.Main")!!

fun main() {
    val dataSource = DatabaseConfig.nais().createDataSource()

    /* Status på migrering, så ready-endepunktet kan fortelle om vi er klare for å motta api-kall. */
    val migrateResult = AtomicReference<MigrateResult>()

    val javalin = startJavalin(
        azureAdConfig = AzureAdConfig.nais(),
        dataSource = dataSource,
        migrateResult = migrateResult,
    )

    while (!dataSource.isReady())  {
        log.info("Database not ready. Sleeping")
        Thread.sleep(100.milliseconds.inWholeMilliseconds)
    }
    migrateResult.set(dataSource.migrate())

    val shutdown = AtomicBoolean(false)

    val kafkaConfig = KafkaConfig.nais()

    val minsideBestillingProducer = kafkaConfig.minsideBestillingsProducer()
    val minsideOppdateringConsumer = kafkaConfig.minsideOppdateringsConsumer()

    val azureTokenClient = AzureTokenClient(
        tokenEndpoint = getenvOrThrow("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
        clientId = getenvOrThrow("AZURE_APP_CLIENT_ID"),
        clientSecret = getenvOrThrow("AZURE_APP_CLIENT_SECRET"),
        scope = "api://${getenv("NAIS_CLUSTER_NAME")}.toi.rekrutteringsbistand-stilling-api/.default"
    )

    val onBehalfOfTokenClient = OnBehalfOfTokenClient(
        tokenEndpoint = getenvOrThrow("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
        clientId = getenvOrThrow("AZURE_APP_CLIENT_ID"),
        clientSecret = getenvOrThrow("AZURE_APP_CLIENT_SECRET"),
        scope = "api://${getenv("NAIS_CLUSTER_NAME")}.toi.rekrutteringsbistand-stilling-api/.default"
    )



    val stillingClient = StillingClientImpl(azureTokenClient)
    val kandidatsokApiKlient = KandidatsokApiKlient(onBehalfOfTokenClient)

    val minsideBestillingThread = backgroundThread("minside-utsending", shutdown) {
        if (!bestillVarsel(dataSource, stillingClient, minsideBestillingProducer)) {
            Thread.sleep(1.seconds.inWholeMilliseconds)
        }
    }

    val minsideOppdateringThread = backgroundThread(name = "minside-oppdatering", shutdown) {
        sjekkVarselOppdateringer(dataSource, minsideOppdateringConsumer)
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        shutdown.set(true)
        minsideBestillingThread.join()
        minsideBestillingProducer.close()
        minsideOppdateringThread.join()
        minsideOppdateringConsumer.close()
        javalin.stop()
        dataSource.close()
        log.info("Shutdownhook ran successfully to completion")
    })
}

private fun backgroundThread(name: String, shutdown: AtomicBoolean, body: () -> Unit): Thread = thread(name = name) {
    while (!shutdown.get()) {
        try {
            body()
        } catch (e: Exception) {
            log.error("Exception in background thread {}", name, e)
            Thread.sleep(1.seconds.inWholeMilliseconds)
        }
    }
}

val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

fun getenvOrThrow(name: String): String = getenv(name) ?: throw IllegalStateException("Mangler miljøvariabel '$name'")
