package no.nav.toi

import no.nav.security.mock.oauth2.MockOAuth2Server
import java.util.*


const val modiaGenerell = "67a06857-0028-4a90-bf4c-9c9a92c7d733"
const val modiaOppfølging = "554a66fb-fbec-4b92-90c1-0d9c085c362c"

private const val authPort = 18306

class LokalApp {
    private val app: App = lagLokalApp()
    private val authServer = MockOAuth2Server()

    fun start() {
        app.start()
        authServer.start(port = authPort)
    }

    fun close() {
        app.close()
        authServer.shutdown()
    }

    fun lagToken(
        issuerId: String = "http://localhost:$authPort/default",
        aud: String = "1",
        navIdent: String = "A000001",
        claims: Map<String, Any> = mapOf("NAVident" to navIdent, "groups" to listOf(modiaGenerell))
    ) = authServer.issueToken(
        issuerId = issuerId,
        subject = "subject",
        audience = aud,
        claims = claims
    )
}

private fun lagLokalApp() = App(
    port = 8080,
    authenticationConfigurations = listOf(
        AuthenticationConfiguration(
            audience = "1",
            issuer = "http://localhost:$authPort/default",
            jwksUri = "http://localhost:$authPort/default/jwks",
        )
    ),
    rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
        modiaGenerell = UUID.fromString(modiaGenerell),
        modiaOppfølging = UUID.fromString(modiaOppfølging),
    ),
)
