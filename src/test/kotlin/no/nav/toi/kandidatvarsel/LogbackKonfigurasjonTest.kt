package no.nav.toi.kandidatvarsel

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.util.LogbackMDCAdapter
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.spi.AppenderAttachable
import ch.qos.logback.core.status.Status
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Logger.ROOT_LOGGER_NAME
import java.io.File

/**
 * Laster prod-oppsettet i src/main/resources/logback.xml. Testene bruker ellers
 * src/test/resources/logback.xml, så uten denne testen oppdages ikke brudd i
 * logback, logstash-logback-encoder, audit-log eller OpenTelemetry-appenderen.
 */
class LogbackKonfigurasjonTest {

    private val prodKonfig = File("src/main/resources/logback.xml")

    private val naisEgenskaper = mapOf(
        "NAIS_CLUSTER_NAME" to "test-gcp",
        "GOOGLE_CLOUD_PROJECT" to "test-prosjekt",
        "NAIS_NAMESPACE" to "toi",
        "HOSTNAME" to "test-pod",
        "NAIS_APP_NAME" to "rekrutteringsbistand-kandidatvarsel-api",
    )

    private fun medKonfig(egenskaper: Map<String, String>, block: (LoggerContext) -> Unit) {
        val context = LoggerContext()
        context.setMDCAdapter(LogbackMDCAdapter())
        egenskaper.forEach { (k, v) -> context.putProperty(k, v) }
        try {
            JoranConfigurator().apply {
                this.context = context
                doConfigure(prodKonfig)
            }
            block(context)
        } finally {
            context.stop()
        }
    }

    private fun advarslerOgFeil(context: LoggerContext) =
        context.statusManager.copyOfStatusList
            .filter { it.effectiveLevel >= Status.WARN }
            .map { it.message }
            // syslog4j (brukt av audit-log) har overlastede settere. Ufarlig, og kommer i alle apper med audit-log.
            .filterNot { it.startsWith("Class 'org.productivity.java.syslog4j") && it.contains("multiple setters") }

    @Suppress("UNCHECKED_CAST")
    private fun konsollAppender(context: LoggerContext): ConsoleAppender<ILoggingEvent> {
        val otel = context.getLogger(ROOT_LOGGER_NAME).getAppender("loggIJsonFormatTilKibana-OTEL")
            as AppenderAttachable<ILoggingEvent>
        return otel.getAppender("loggIJsonFormatTilKibana") as ConsoleAppender<ILoggingEvent>
    }

    @Test
    fun `prod-oppsett lastes utenfor Nais uten team-logs og audit-logg`() {
        medKonfig(emptyMap()) { context ->
            // team-logs finnes bare i Nais, så referansen fra team-logs-OTEL gir en forventet advarsel
            assertThat(advarslerOgFeil(context)).containsExactly(
                "Appender named [team-logs] could not be found. Skipping attachment to " +
                    "io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender[team-logs-OTEL]."
            )
            assertThat(konsollAppender(context).isStarted).isTrue
            assertThat(context.exists("AuditLogger")).isNull()
        }
    }

    @Test
    fun `prod-oppsett i Nais konfigurerer team-logs og audit-logg`() {
        medKonfig(naisEgenskaper) { context ->
            // team-logs.nais-system finnes bare i clusteret. TCP-appenderen kobler til asynkront,
            // så antall nettverksadvarsler varierer mellom kjøringer.
            assertThat(advarslerOgFeil(context).filterNot { it.contains("team-logs.nais-system") }).isEmpty()

            val root = context.getLogger(ROOT_LOGGER_NAME)
            val teamLogsOtel = root.getAppender("team-logs-OTEL") as AppenderAttachable<*>
            assertThat(teamLogsOtel.getAppender("team-logs")).isNotNull
            assertThat(teamLogsOtel.getAppender("team-logs").isStarted).isTrue

            val auditLogger = context.exists("AuditLogger")
            assertThat(auditLogger).isNotNull
            assertThat(auditLogger!!.isAdditive).isFalse
            assertThat(auditLogger.getAppender("AuditLogger")).isNotNull
        }
    }

    @Test
    fun `konsoll-appender skriver JSON med melding og nivå`() {
        medKonfig(emptyMap()) { context ->
            val logger = context.getLogger("no.nav.toi.test")
            val event = LoggingEvent(logger.javaClass.name, logger, Level.INFO, "hei fra {}", null, arrayOf("test"))

            val linje = String(konsollAppender(context).encoder.encode(event))

            val json = ObjectMapper().readTree(linje)
            assertThat(json["message"].asText()).isEqualTo("hei fra test")
            assertThat(json["level"].asText()).isEqualTo("INFO")
            assertThat(json["logger_name"].asText()).isEqualTo("no.nav.toi.test")
            assertThat(json.has("@timestamp")).isTrue
        }
    }
}
