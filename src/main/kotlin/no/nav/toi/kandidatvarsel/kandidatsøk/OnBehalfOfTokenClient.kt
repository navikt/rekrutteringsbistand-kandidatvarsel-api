package auth.obo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.getOrElse
import io.javalin.http.Context
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TokenResponse(
    @Suppress("PropertyName") val access_token: String,
    @Suppress("PropertyName") val expires_in: Long,
)

class OnBehalfOfTokenClient(private val scope: String, private val tokenEndpoint: String, private val clientId: String, private val clientSecret: String) {

    // Midlertidig kommentar: I dev er denne satt i MainKt.run.xml, og hentes fra fakedings
    private val issuernavn = System.getenv("AZURE_OPENID_CONFIG_ISSUER")

    companion object {
        const val AZURE_ON_BEHALF_OF_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    fun hentTokenSomString(ctx: Context): String {
        val validerteTokens = todo.hentValiderteTokens(ctx) // TODO


        // Filtrer ut riktig issuer før vi velger firstOrNull
        val issuerUrl = validerteTokens.issuers.firstOrNull { it == issuernavn }
            ?: throw RuntimeException("Ingen issuer funnet som matcher: $issuernavn")

        return validerteTokens.getJwtToken(issuerUrl)?.tokenAsString
            ?: throw RuntimeException("Ingen gyldig token funnet for issuer: $issuerUrl")
    }

    fun oboToken(ctx: Context, navIdent: String): String {
        val cacheKey = "$scope-$navIdent" // TODO: Imlpementer cache pr navIdent med ehcache, se forespørsel-om-deling-av-cv
        val tokenResponse = fetchOboToken(ctx, navIdent)

        return tokenResponse.access_token
    }

    private fun fetchOboToken(ctx: Context, navIdent: String): TokenResponse  {
        val innkommendeToken = hentTokenSomString(ctx)

        val (_, response, responseBody) = Fuel.post(
            tokenEndpoint,
            listOf(
                "grant_type" to AZURE_ON_BEHALF_OF_GRANT_TYPE,
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "assertion" to innkommendeToken,
                "scope" to scope,
                "requested_token_use" to REQUESTED_TOKEN_USE
            )
        )
            .responseObject<TokenResponse>()

        if (response.statusCode != 200) {
            throw RuntimeException("Failed to get token: ${response.responseMessage}")
        }

        return responseBody.getOrElse { fuelError ->
            throw RuntimeException("Failed to get token: ${fuelError.response.responseMessage}")
        }
        /*
        val cacheKey = "$scope-$navIdent"
        val innkommendeToken = hentTokenSomString(ctx)

        val formData = listOf(
            "grant_type" to AZURE_ON_BEHALF_OF_GRANT_TYPE,
            "client_id" to System.getenv("AZURE_APP_CLIENT_ID"),
            "client_secret" to config.azureClientSecret,
            "assertion" to innkommendeToken,
            "scope" to scope,
            "requested_token_use" to REQUESTED_TOKEN_USE
        )

        return getToken(cacheKey, formData)*/
    }
}
