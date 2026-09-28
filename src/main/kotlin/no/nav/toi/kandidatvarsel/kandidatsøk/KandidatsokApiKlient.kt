package auth.obo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import no.nav.toi.kandidatvarsel.httpRequest
import no.nav.toi.kandidatvarsel.log
import no.nav.toi.kandidatvarsel.standardHttpClient
import org.eclipse.jetty.http.HttpStatus
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class KandidatsokApiKlient(
    private val onBehalfOfTokenClient: OnBehalfOfTokenClient,
    private val kandidatsokUrl: String,
    private val httpClient: HttpClient = standardHttpClient,
) {

    fun verifiserKandidatTilgang(ctx: Context, navIdent: String, fnr: String) {
        val url = "$kandidatsokUrl/api/brukertilgang"
        val body = BrukertilgangRequestDto(fodselsnummer = fnr, aktorid = null, kandidatnr = null)
        val token = onBehalfOfTokenClient.oboToken(ctx)

        val request = httpRequest(url)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .POST(HttpRequest.BodyPublishers.ofString(body.toJson()))
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            log.error("Kan ikke verifisere tilgang mot bruker, kallet mot kandidatsøket feilet", e)
            throw HttpResponseException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Feil ved verifisering av tilgang")
        }

        when (response.statusCode()) {
            in 200..299 -> log.info("Tilgang verifisert: ${response.body()}")
            else -> handleFailure(response.statusCode())
        }
    }

    private fun handleFailure(statusCode: Int) {
        when (statusCode) {
            404 -> {
                log.info("Kan ikke verifisere tilgang mot bruker, får http 404 fra kandidatsøket")
                throw HttpResponseException(HttpStatus.NOT_FOUND_404, "Ikke funnet")
            }

            403 -> {
                log.info("403 Mangler tilgang til persondata")
                throw HttpResponseException(HttpStatus.FORBIDDEN_403, "Ikke tilgang")
            }

            else -> {
                log.error("Kan ikke verifisere tilgang mot bruker, får http $statusCode fra kandidatsøket")
                throw HttpResponseException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Feil ved verifisering av tilgang")
            }
        }
    }

    private data class BrukertilgangRequestDto(
        val fodselsnummer: String?,
        val aktorid: String?,
        val kandidatnr: String?
    ) {

        fun toJson() =
            jacksonObjectMapper().writeValueAsString(this)
    }
}
