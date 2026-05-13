package auth.obo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import no.nav.toi.kandidatvarsel.log
import org.eclipse.jetty.http.HttpStatus

class KandidatsokApiKlient(private val onBehalfOfTokenClient: OnBehalfOfTokenClient, private val kandidatsokUrl: String) {

    fun verifiserKandidatTilgang(ctx: Context, navIdent: String, fnr: String) {
        val url = "$kandidatsokUrl/api/brukertilgang"
        val body = BrukertilgangRequestDto(fodselsnummer = fnr, aktorid = null, kandidatnr = null)
        val token = onBehalfOfTokenClient.oboToken(ctx)

        val (_, response, result) = Fuel.post(url)
            .header(Headers.CONTENT_TYPE,  "application/json")
            .authentication().bearer(token)
            .jsonBody(body.toJson())
            .response()

        when (result) {
            is Result.Success -> log.info("Tilgang verifisert: ${response.body().asString("application/json")}")
            is Result.Failure -> handleFailure(response)
        }
    }

    private fun handleFailure(response: com.github.kittinunf.fuel.core.Response) {
        when (response.statusCode) {
            404 -> {
                log.info("Kan ikke verifisere tilgang mot bruker, får http 404 fra kandidatsøket")
                throw HttpResponseException(HttpStatus.NOT_FOUND_404, "Ikke funnet")
            }

            403 -> {
                log.info("403 Mangler tilgang til persondata")
                throw HttpResponseException(HttpStatus.FORBIDDEN_403, "Ikke tilgang")
            }

            else -> {
                log.error("Kan ikke verifisere tilgang mot bruker, får http ${response.statusCode} fra kandidatsøket")
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
