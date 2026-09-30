package no.nav.toi.kandidatvarsel

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class HttpKlientTest {

    /**
     * Rå socket i stedet for WireMock, fordi WireMock ikke sender headerne før den første biten av en treg body.
     * Serveren holder forbindelsen i maks 5 sekunder, så testen feiler i stedet for å henge om fristen ikke virker.
     */
    @Test
    fun `gir opp når body stopper opp etter at headerne er mottatt`() {
        val slippForbindelsen = CountDownLatch(1)
        ServerSocket(0).use { server ->
            thread(isDaemon = true) {
                server.accept().use { socket ->
                    val forespørsel = socket.getInputStream().bufferedReader()
                    while (forespørsel.readLine()?.isNotEmpty() == true) {
                        // leser forbi request-headerne
                    }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n{\"ufullstendig\":".toByteArray())
                        flush()
                    }
                    slippForbindelsen.await(5, TimeUnit.SECONDS)
                }
            }

            val start = System.nanoTime()
            val feil = catchThrowable {
                standardHttpClient.sendOgHentTekst(
                    httpRequest("http://localhost:${server.localPort}/").GET().build(),
                    tidsfrist = Duration.ofMillis(500),
                )
            }
            val brukt = Duration.ofNanos(System.nanoTime() - start)
            slippForbindelsen.countDown()

            assertThat(feil).isInstanceOf(HttpTimeoutException::class.java)
            assertThat(brukt).isLessThan(Duration.ofSeconds(3))
        }
    }
}
