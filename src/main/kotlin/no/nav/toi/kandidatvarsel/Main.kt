package no.nav.toi.kandidatvarsel

import no.nav.toi.kandidatvarsel.minside.bestillVarsel
import no.nav.toi.kandidatvarsel.minside.sjekkVarselOppdateringer
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun main() {
    val log = LoggerFactory.getLogger("no.nav.toi.kandidatvarsel.Main")!!

    val dataSource = DatabaseConfig.nais().createDataSource()

    val kafkaConfig = KafkaConfig.nais()
    val minsideBestillingProducer = kafkaConfig.minsideBestillingsProducer()
    val minsideOppdateringConsumer = kafkaConfig.minsideOppdateringsConsumer()

    /* Status på migrering, så ready-endepunktet kan fortelle om vi er klare for å motta api-kall. */
    val migrateResult = AtomicReference<MigrateResult>()

    val javalin = startJavalin(
        azureAdConfig = AzureAdConfig.nais(),
        dataSource = dataSource,
        migrateResult = migrateResult,
        nyTilgangsstyring = when (System.getenv("NAIS_CLUSTER_NAME")) {
            "dev-gcp" -> false
            "prod-gcp" -> false
            else -> throw IllegalStateException("Ukjent cluster: ${System.getenv("NAIS_CLUSTER_NAME")}")
        }
    )

    while (!dataSource.isReady())  {
        log.info("Database not ready. Sleeping")
        Thread.sleep(100.milliseconds.inWholeMilliseconds)
    }
    migrateResult.set(dataSource.migrate())

    val shutdown = AtomicBoolean(false)

    val minsideBestillingThread = thread(name = "minside-utsending") {
        while (!shutdown.get()) {
            try {
                if (!bestillVarsel(dataSource, minsideBestillingProducer)) {
                    Thread.sleep(1.seconds.inWholeMilliseconds)
                }
            } catch (e: Exception) {
                log.error("Exception {} ved utsending av varsel", e::class.qualifiedName, e)
                Thread.sleep(1.seconds.inWholeMilliseconds)
            }
        }
    }

    val minsideOppdateringThread = thread(name = "minside-oppdatering") {
        while (!shutdown.get()) {
            try {
                sjekkVarselOppdateringer(dataSource, minsideOppdateringConsumer)
            } catch (e: Exception) {
                log.error("Exception {} ved oppdatering av varsel", e::class.qualifiedName, e)
                Thread.sleep(1.seconds.inWholeMilliseconds)
            }
        }
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


val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

fun getenvOrThrow(name: String): String = System.getenv(name) ?: throw IllegalStateException("Mangler miljøvariabel '$name'")
