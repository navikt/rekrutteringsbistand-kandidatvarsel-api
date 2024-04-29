package no.nav.toi.kandidatvarsel

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.getOrElse
import org.slf4j.LoggerFactory
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
): StillingClient {
    private val log = LoggerFactory.getLogger("no.nav.toi.kandidatvarsel.Stilling")!!

    private val baseUrl = "http://rekrutteringsbistand-stilling-api.toi.svc.cluster.local"

    override fun getStilling(stillingId: UUID): Stilling? {
        val (_, response, result) = Fuel.get("$baseUrl/rekrutteringsbistand/ekstern/api/v1/stilling/${stillingId}")
            .header("Authorization", "Bearer ${azureTokenClient.authToken()}")
            .responseObject<Stilling>()

        if (response.statusCode != 200) {
            log.error("getStilling({}) feilet med http status {}", stillingId, response.statusCode)
            return null
        }

        return result.getOrElse {
            log.error("getStilling({}) feilet", stillingId)
            null
        }
    }
}

