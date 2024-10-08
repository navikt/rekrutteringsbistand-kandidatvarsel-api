package auth.obo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import org.eclipse.jetty.http.HttpStatus
import org.slf4j.LoggerFactory

class KandidatsokApiKlient(private val onBehalfOfTokenClient: OnBehalfOfTokenClient, private val kandidatsokUrl: String) {

    private val logger = LoggerFactory.getLogger(KandidatsokApiKlient::class.java)

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
            is Result.Success -> logger.info("Tilgang verifisert: ${response.body().asString("application/json")}")
            is Result.Failure -> handleFailure(response)
        }
    }

    private fun handleFailure(response: com.github.kittinunf.fuel.core.Response) {
        when (response.statusCode) {
            404 -> {
                logger.info("Kan ikke verifisere tilgang mot bruker, får http 404 fra kandidatsøket")
                throw HttpResponseException(HttpStatus.NOT_FOUND_404, "Ikke funnet")
            }

            403 -> {
                logger.info("403 Mangler tilgang til persondata")
                throw HttpResponseException(HttpStatus.FORBIDDEN_403, "Ikke tilgang")
            }

            else -> {
                logger.error("Kan ikke verifisere tilgang mot bruker, får http ${response.statusCode} fra kandidatsøket")
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
