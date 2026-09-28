package no.nav.toi.kandidatvarsel

import java.net.http.HttpClient
import java.time.Instant

class AzureTokenClient(
    private val tokenEndpoint: String,
    private val clientId: String,
    private val clientSecret: String,
    private val scope: String,
    private val httpClient: HttpClient = standardHttpClient,
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

    private fun fetchAuthToken(): TokenResponse = hentTokenFraAzure(
        httpClient,
        tokenEndpoint,
        mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "grant_type" to "client_credentials",
            "scope" to scope,
        )
    )
}
