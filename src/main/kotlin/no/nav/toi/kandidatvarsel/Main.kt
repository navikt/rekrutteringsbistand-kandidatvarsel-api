package no.nav.toi.kandidatvarsel

import auth.obo.KandidatsokApiKlient
import auth.obo.OnBehalfOfTokenClient
import com.github.navikt.tbd_libs.rapids_and_rivers.KafkaRapid
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.toi.kandidatvarsel.minside.bestillVarsel
import no.nav.toi.kandidatvarsel.minside.sjekkVarselOppdateringer
import no.nav.toi.kandidatvarsel.rapids.lyttere.KandidatInvitertTreffEndretLytter
import no.nav.toi.kandidatvarsel.rapids.lyttere.KandidatInvitertLytter
import no.nav.toi.kandidatvarsel.rapids.lyttere.KandidatTreffAvlystLytter
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.System.getenv
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("no.nav.toi.kandidatvarsel.Main")!!
val secureLog = SecureLog(log)

fun main() {
    log.info("Starter applikasjon")
    secureLog.info("Starter applikasjon. Dette er ment å logges til Securelogs. Hvis du ser dette i den ordinære apploggen er noe galt, og sensitive data kan havne i feil logg.")
    
    try {
        val aivenConfig = AivenConfig.default
        val factory = ConsumerProducerFactory(aivenConfig)
        
        val kafkaRapid = KafkaRapid(
            factory = factory,
            groupId = getenvOrThrow("KAFKA_CONSUMER_GROUP_ID"),
            rapidTopic = getenvOrThrow("KAFKA_RAPID_TOPIC"),
            meterRegistry = SimpleMeterRegistry()
        )
        val dataSource = DatabaseConfig.nais().createDataSource()
        
        startOppApplikasjon(
            kafkaRapid = kafkaRapid,
            dataSource = dataSource
        )
    } catch (e: Exception) {
        secureLog.error("Uhåndtert exception, stanser applikasjonen", e)
        log.error("Uhåndtert exception, stanser applikasjonen (se securelog)")
        exitProcess(1)
    }
}

fun startOppApplikasjon(
    kafkaRapid: KafkaRapid,
    dataSource: HikariDataSource
) {
    val migreringsResultat = AtomicReference<MigrateResult>()
    val avsluttSignal = AtomicBoolean(false)

    ventPåDatabase(dataSource)
    migreringsResultat.set(dataSource.migrate())

    val kafkaConfig = KafkaConfig.nais()
    val minsideBestillingProducer = kafkaConfig.minsideBestillingsProducer()
    val minsideOppdateringConsumer = kafkaConfig.minsideOppdateringsConsumer()

    val azureTokenClient = opprettAzureTokenClient()
    val onBehalfOfTokenClient = opprettOnBehalfOfTokenClient()
    val stillingClient = StillingClientImpl(azureTokenClient)
    val kandidatsokApiKlient = KandidatsokApiKlient(onBehalfOfTokenClient, getenvOrThrow("KANDIDATSOK_API_URL"))

    val minsideBestillingThread = backgroundThread("minside-utsending", avsluttSignal) {
        if (!bestillVarsel(dataSource, stillingClient, minsideBestillingProducer)) {
            Thread.sleep(1.seconds.inWholeMilliseconds)
        }
    }

    val minsideOppdateringThread = backgroundThread("minside-oppdatering", avsluttSignal) {
        sjekkVarselOppdateringer(dataSource, minsideOppdateringConsumer, kafkaRapid)
    }

    val javalin = startJavalin(
        azureAdConfig = AzureAdConfig.nais(),
        dataSource = dataSource,
        migrateResult = migreringsResultat,
        kandidatsokApiKlient = kandidatsokApiKlient,
        isRapidRunning = kafkaRapid::isRunning
    )

    registrerRapidsLyttere(kafkaRapid, dataSource)
    
    val kafkaRapidThread = backgroundThread(navn = "kafka-rapid", timeoutvarighet = 30.seconds, avsluttSignal = avsluttSignal) {
        kafkaRapid.start()
    }
    log.info("KafkaRapid startet i bakgrunnstråd")

    registrerShutdownHook(
        avsluttSignal = avsluttSignal,
        rapidsConnection = kafkaRapid,
        kafkaRapidThread = kafkaRapidThread,
        minsideBestillingThread = minsideBestillingThread,
        minsideBestillingProducer = minsideBestillingProducer,
        minsideOppdateringThread = minsideOppdateringThread,
        minsideOppdateringConsumer = minsideOppdateringConsumer,
        javalin = javalin,
        dataSource = dataSource
    )
}

private fun ventPåDatabase(dataSource: HikariDataSource) {
    while (!dataSource.isReady()) {
        log.info("Database er ikke klar. Venter...")
        Thread.sleep(100.milliseconds.inWholeMilliseconds)
    }
}

private fun opprettAzureTokenClient() = AzureTokenClient(
    tokenEndpoint = getenvOrThrow("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    clientId = getenvOrThrow("AZURE_APP_CLIENT_ID"),
    clientSecret = getenvOrThrow("AZURE_APP_CLIENT_SECRET"),
    scope = "api://${getenv("NAIS_CLUSTER_NAME")}.toi.rekrutteringsbistand-stilling-api/.default"
)

private fun opprettOnBehalfOfTokenClient() = OnBehalfOfTokenClient(
    tokenEndpoint = getenvOrThrow("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    clientId = getenvOrThrow("AZURE_APP_CLIENT_ID"),
    clientSecret = getenvOrThrow("AZURE_APP_CLIENT_SECRET"),
    scope = "api://${getenv("NAIS_CLUSTER_NAME")}.toi.rekrutteringsbistand-kandidatsok-api/.default",
    issuernavn = getenvOrThrow("AZURE_OPENID_CONFIG_ISSUER")
)

private fun registrerRapidsLyttere(rapidsConnection: RapidsConnection, dataSource: HikariDataSource) {
    try {
        KandidatInvitertLytter(rapidsConnection, dataSource)
        KandidatInvitertTreffEndretLytter(rapidsConnection, dataSource)
        KandidatTreffAvlystLytter(rapidsConnection, dataSource)
    } catch (e: Exception) {
        log.error("Feil ved oppstart av RapidApplication (se securelog)")
        secureLog.error("Feil ved oppstart av RapidApplication", e)
        throw e
    }
}

private fun registrerShutdownHook(
    avsluttSignal: AtomicBoolean,
    rapidsConnection: RapidsConnection,
    kafkaRapidThread: Thread,
    minsideBestillingThread: Thread,
    minsideBestillingProducer: org.apache.kafka.clients.producer.KafkaProducer<String, String>,
    minsideOppdateringThread: Thread,
    minsideOppdateringConsumer: org.apache.kafka.clients.consumer.KafkaConsumer<String, String>,
    javalin: io.javalin.Javalin,
    dataSource: HikariDataSource
) {
    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutdownhook kjører")
        avsluttSignal.set(true)
        rapidsConnection.stop()
        kafkaRapidThread.join()
        minsideBestillingThread.join()
        minsideBestillingProducer.close()
        minsideOppdateringThread.join()
        minsideOppdateringConsumer.close()
        javalin.stop()
        dataSource.close()
        log.info("Shutdownhook fullført")
    })
}

private fun backgroundThread(navn: String, avsluttSignal: AtomicBoolean, timeoutvarighet: Duration = 1.seconds, oppgave: () -> Unit): Thread =
    thread(name = navn) {
        while (!avsluttSignal.get()) {
            try {
                oppgave()
            } catch (e: Exception) {
                log.error("Exception i bakgrunnsThread $navn (se secure log)")
                secureLog.error("Exception i bakgrunnsThread $navn", e)
                Thread.sleep(timeoutvarighet.inWholeMilliseconds)
            }
        }
    }

val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

fun getenvOrThrow(name: String): String = 
    getenv(name) ?: throw IllegalStateException("Mangler miljøvariabel '$name'")
