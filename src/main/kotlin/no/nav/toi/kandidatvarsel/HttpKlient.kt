package no.nav.toi.kandidatvarsel

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val tidsavbrudd = Duration.ofSeconds(15)

val standardHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(tidsavbrudd)
    .build()

internal val httpObjectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

internal fun httpRequest(url: String): HttpRequest.Builder =
    HttpRequest.newBuilder(URI.create(url)).timeout(tidsavbrudd)

/**
 * Som [HttpClient.send], men med én tidsfrist for hele kallet, også lesingen av body.
 * [HttpRequest.Builder.timeout] gjelder bare til status og headere er mottatt.
 *
 * Tidsavbrudd og avbrudd blir [IOException], så kallere trenger bare å håndtere den.
 * Ved avbrudd settes interrupt-flagget tilbake.
 */
internal fun HttpClient.sendOgHentTekst(
    request: HttpRequest,
    tidsfrist: Duration = tidsavbrudd,
): HttpResponse<String> {
    val svar = sendAsync(request, HttpResponse.BodyHandlers.ofString())
    try {
        return svar.get(tidsfrist.toMillis(), TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        svar.cancel(true)
        throw HttpTimeoutException("Kall til ${request.uri().host} tok mer enn ${tidsfrist.toMillis()} ms")
            .apply { initCause(e) }
    } catch (e: InterruptedException) {
        svar.cancel(true)
        Thread.currentThread().interrupt()
        throw InterruptedIOException("Kall til ${request.uri().host} ble avbrutt").apply { initCause(e) }
    } catch (e: ExecutionException) {
        val årsak = e.cause ?: e
        throw if (årsak is IOException || årsak is RuntimeException || årsak is Error) årsak else IOException(årsak)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TokenResponse(
    @Suppress("PropertyName") val access_token: String,
    @Suppress("PropertyName") val expires_in: Long,
)

internal fun hentTokenFraAzure(
    httpClient: HttpClient,
    tokenEndpoint: String,
    parametre: Map<String, String>,
): TokenResponse {
    val body = parametre.entries.joinToString("&") { (navn, verdi) ->
        "${URLEncoder.encode(navn, Charsets.UTF_8)}=${URLEncoder.encode(verdi, Charsets.UTF_8)}"
    }
    val request = httpRequest(tokenEndpoint)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()

    val response = try {
        httpClient.sendOgHentTekst(request)
    } catch (e: IOException) {
        throw RuntimeException("Failed to get token: ${e.javaClass.simpleName}", e)
    }

    if (response.statusCode() != 200) {
        throw RuntimeException("Failed to get token: HTTP ${response.statusCode()}")
    }

    return try {
        httpObjectMapper.readValue<TokenResponse>(response.body())
    } catch (e: JacksonException) {
        throw RuntimeException("Failed to get token: ugyldig svar fra token-endepunktet", e)
    }
}
