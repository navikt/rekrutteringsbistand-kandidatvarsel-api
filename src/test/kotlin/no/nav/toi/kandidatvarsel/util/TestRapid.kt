package no.nav.toi.kandidatvarsel.util

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid as TbdTestRapid

/**
 * En wrapper rundt tbd-libs TestRapid med løkkedeteksjon for testing.
 * Delegaterer til den ekte TestRapid, men wrapper sendTestMessage for å detektere uendelige løkker.
 */
class TestRapid(
    meterRegistry: MeterRegistry = SimpleMeterRegistry(),
    private val maxTriggedeMeldinger: Int = 10
) {
    val delegate = TbdTestRapid(meterRegistry)
    
    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    val inspektør: RapidInspector
        get() = RapidInspector(delegate.inspektør)

    fun reset() {
        delegate.reset()
    }

    fun sendTestMessage(message: String) {
        delegate.sendTestMessage(message)
        sjekkForLoop()
    }

    fun sendTestMessage(message: String, key: String) {
        delegate.sendTestMessage(message, key)
        sjekkForLoop()
    }

    private fun sjekkForLoop() {
        val sizeBefore = delegate.inspektør.size
        if (sizeBefore > maxTriggedeMeldinger) {
            throw IllegalStateException("Loop antatt med $sizeBefore meldinger")
        }
    }

    class RapidInspector(private val delegate: TbdTestRapid.RapidInspector) {
        val size get() = delegate.size

        fun key(index: Int) = delegate.key(index)
        fun message(index: Int) = delegate.message(index)
        fun field(index: Int, field: String) = delegate.field(index, field)
    }
}