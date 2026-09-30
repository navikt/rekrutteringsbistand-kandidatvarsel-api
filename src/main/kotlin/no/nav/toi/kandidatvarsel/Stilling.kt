package no.nav.toi.kandidatvarsel

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.IOException
import java.net.http.HttpClient
import java.util.*

data class Stilling(
    val title: String,
    val businessName: String,
)

interface StillingClient {
    fun getStilling(stillingId: UUID): Stilling?
}

class StillingClientImpl(
    private val azureTokenClient: AzureTokenClient,
    private val baseUrl: String = "http://rekrutteringsbistand-stilling-api.toi.svc.cluster.local",
    private val httpClient: HttpClient = standardHttpClient,
): StillingClient {

    override fun getStilling(stillingId: UUID): Stilling? {
        val request = httpRequest("$baseUrl/rekrutteringsbistand/ekstern/api/v1/stilling/${stillingId}")
            .header("Authorization", "Bearer ${azureTokenClient.authToken()}")
            .GET()
            .build()

        val response = try {
            httpClient.sendOgHentTekst(request)
        } catch (e: IOException) {
            log.error("getStilling({}) feilet", stillingId, e)
            return null
        }

        if (response.statusCode() != 200) {
            log.error("getStilling({}) feilet med http status {}", stillingId, response.statusCode())
            return null
        }

        return try {
            httpObjectMapper.readValue<Stilling>(response.body())
        } catch (e: JacksonException) {
            log.error("getStilling({}) feilet", stillingId, e)
            null
        }
    }
}

