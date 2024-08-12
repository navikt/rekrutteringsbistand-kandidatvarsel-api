package auth.obo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.getOrElse
import io.javalin.http.Context
import io.javalin.http.UnauthorizedResponse
import no.nav.toi.kandidatvarsel.AzureAdConfig
import no.nav.toi.kandidatvarsel.hentToken

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TokenResponse(
    @Suppress("PropertyName") val access_token: String,
    @Suppress("PropertyName") val expires_in: Long,
)

class OnBehalfOfTokenClient(private val scope: String, private val tokenEndpoint: String, private val clientId: String, private val clientSecret: String, private val issuernavn: String) {

    companion object {
        const val AZURE_ON_BEHALF_OF_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    private fun hentTokenSomString(ctx: Context): String {
        val token = ctx.hentToken() ?: throw UnauthorizedResponse("Mangler token")
        val decodedToken = AzureAdConfig.nais().verify(token)
        val issuerUrl = decodedToken.issuer
        if (issuerUrl != issuernavn) {
            throw RuntimeException("Token issuer does not match expected issuer: $issuerUrl")
        }

        return token
    }

    fun oboToken(ctx: Context): String {
        val tokenResponse = fetchOboToken(ctx)

        return tokenResponse.access_token
    }

    private fun fetchOboToken(ctx: Context): TokenResponse  {
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
    }
}
