package no.nav.toi.kandidatvarsel

import auth.obo.KandidatsokApiKlient
import auth.obo.OnBehalfOfTokenClient
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.toi.kandidatvarsel.minside.bestillVarsel
import no.nav.toi.kandidatvarsel.minside.sjekkVarselOppdateringer
import no.nav.toi.kandidatvarsel.rapids.lyttere.InvitertTreffKandidatEndretLytter
import no.nav.toi.kandidatvarsel.rapids.lyttere.KandidatInvitertLytter
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.System.getenv
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("no.nav.toi.kandidatvarsel.Main")!!

fun main() {
    log.info("Starter applikasjon")
    
    try {
        lateinit var rapidIsAlive: () -> Boolean
        val rapidsConnection = RapidApplication.create(
            System.getenv(),
            builder = { withHttpPort(9000) },
            configure = { _, kafkaRapid ->
                rapidIsAlive = kafkaRapid::isRunning
            }
        )
        
        val dataSource = DatabaseConfig.nais().createDataSource()
        
        startApp(
            rapidsConnection = rapidsConnection,
            dataSource = dataSource,
            rapidIsAlive = rapidIsAlive
        )
    } catch (e: Exception) {
        secureLog.error("Uhåndtert exception, stanser applikasjonen", e)
        log.error("Uhåndtert exception, stanser applikasjonen (se securelog)")
        exitProcess(1)
    }
}

fun startApp(
    rapidsConnection: com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection,
    dataSource: com.zaxxer.hikari.HikariDataSource,
    rapidIsAlive: () -> Boolean
) {
    /* Status på migrering, så ready-endepunktet kan fortelle om vi er klare for å motta api-kall. */
    val migrateResult = AtomicReference<MigrateResult>()

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
        scope = "api://${getenv("NAIS_CLUSTER_NAME")}.toi.rekrutteringsbistand-kandidatsok-api/.default",
        issuernavn = getenvOrThrow("AZURE_OPENID_CONFIG_ISSUER")
    )

    val stillingClient = StillingClientImpl(azureTokenClient)
    val kandidatsokApiKlient = KandidatsokApiKlient(onBehalfOfTokenClient, getenvOrThrow("KANDIDATSOK_API_URL"))

    val minsideBestillingThread = backgroundThread("minside-utsending", shutdown) {
        if (!bestillVarsel(dataSource, stillingClient, minsideBestillingProducer)) {
            Thread.sleep(1.seconds.inWholeMilliseconds)
        }
    }

    val minsideOppdateringThread = backgroundThread(name = "minside-oppdatering", shutdown) {
        sjekkVarselOppdateringer(dataSource, minsideOppdateringConsumer)
    }

    val javalin = startJavalin(
        azureAdConfig = AzureAdConfig.nais(),
        dataSource = dataSource,
        migrateResult = migrateResult,
        kandidatsokApiKlient = kandidatsokApiKlient,
        rapidIsAlive = rapidIsAlive
    )

    // Registrer lyttere for Kafka-hendelser
    try {
        KandidatInvitertLytter(rapidsConnection, dataSource)
        InvitertTreffKandidatEndretLytter(rapidsConnection, dataSource)
        
        rapidsConnection.start()
        log.info("RapidApplication startet")
    } catch (e: Exception) {
        log.error("Feil ved oppstart av RapidApplication (se securelog)")
        secureLog.error("Feil ved oppstart av RapidApplication", e)
        throw e
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutdownhook kjører")
        shutdown.set(true)
        rapidsConnection.stop()
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
            log.error("Exception in background thread $name (Se secure log)")
            secureLog.error("Exception in background thread $name", e)
            Thread.sleep(1.seconds.inWholeMilliseconds)
        }
    }
}

val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

fun getenvOrThrow(name: String): String = getenv(name) ?: throw IllegalStateException("Mangler miljøvariabel '$name'")
