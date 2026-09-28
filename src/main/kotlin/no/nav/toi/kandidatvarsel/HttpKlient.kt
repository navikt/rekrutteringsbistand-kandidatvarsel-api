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
import java.time.Duration

private val tidsavbrudd = Duration.ofSeconds(15)

val standardHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(tidsavbrudd)
    .build()

internal val httpObjectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

internal fun httpRequest(url: String): HttpRequest.Builder =
    HttpRequest.newBuilder(URI.create(url)).timeout(tidsavbrudd)

/**
 * Som [HttpClient.send], men avbrudd gjøres om til [InterruptedIOException] etter at interrupt-flagget er satt
 * tilbake. Da holder det at kallere håndterer [IOException].
 */
internal fun HttpClient.sendOgHentTekst(request: HttpRequest): HttpResponse<String> =
    try {
        send(request, HttpResponse.BodyHandlers.ofString())
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw InterruptedIOException("Kall til ${request.uri().host} ble avbrutt").apply { initCause(e) }
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
