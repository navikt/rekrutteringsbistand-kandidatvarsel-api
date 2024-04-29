package no.nav.toi.kandidatvarsel

import com.github.kittinunf.fuel.Fuel
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.getOrElse
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TokenResponse(
    @Suppress("PropertyName") val access_token: String,
    @Suppress("PropertyName") val expires_in: Long,
)

class AzureTokenClient(
    private val tokenEndpoint: String,
    private val clientId: String,
    private val clientSecret: String,
    private val scope: String,
) {
    private var cachedToken: String = ""
    private var validUntil: Instant = Instant.now().minusSeconds(1)

    fun authToken(): String {
        if (Instant.now().isAfter(validUntil)) {
            val tokenResponse = fetchAuthToken()
            cachedToken = tokenResponse.access_token
            validUntil = Instant.now().plusSeconds(tokenResponse.expires_in).minusSeconds(10)
        }
        return cachedToken
    }

    private fun fetchAuthToken(): TokenResponse {
        val (_, response, responseBody) = Fuel.post(
            tokenEndpoint,
            listOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "grant_type" to "client_credentials",
                "scope" to scope,
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


