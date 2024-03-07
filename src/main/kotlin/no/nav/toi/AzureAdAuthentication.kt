package no.nav.toi

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.MissingClaimException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.RSAKeyProvider
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import org.eclipse.jetty.http.HttpHeader
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.*
import java.util.concurrent.TimeUnit

private const val navIdentClaim = "NAVident"

/**
 * Representerer en autensiert bruker
 */
class AuthenticatedUser(
    val navIdent: String,
    val roller: Set<Rolle>,
) {
    companion object {
        fun fromJwt(jwt: DecodedJWT, rolleUuidSpesifikasjon: RolleUuidSpesifikasjon) =
            AuthenticatedUser(
                navIdent = jwt.getClaim(navIdentClaim).asString(),
                roller = jwt.getClaim("groups")
                    .asList(UUID::class.java)
                    .let { rolleUuidSpesifikasjon.rollerForUuider(it) },
            )
    }
}

/**
 * Henter ut en autensiert bruker fra en kontekst. Kaster InternalServerErrorResponse om det ikke finnes en autensiert bruker
 */
fun Context.authenticatedUser() = attribute<AuthenticatedUser>("authenticatedUser")
    ?: run {
        log.error("No authenticated user found!")
        throw InternalServerErrorResponse()
    }

data class AuthenticationConfiguration(
    val issuer: String,
    val jwksUri: String,
    val audience: String,
)

/**
 * Setter opp token-verifisering på en path på Javalin-serveren
 */
fun Javalin.azureAdAuthentication(
    path: String,
    authenticationConfigurations: List<AuthenticationConfiguration>,
    rolleUuidSpesifikasjon: RolleUuidSpesifikasjon,
): Javalin? {
    val verifiers = authenticationConfigurations.map { jwtVerifier(it) }
    return before(path) { ctx ->
        val jwt = verifyJwt(verifiers, ctx.hentToken())

        ctx.attribute("authenticatedUser", AuthenticatedUser.fromJwt(jwt, rolleUuidSpesifikasjon))
    }
        .exception(JWTVerificationException::class.java) { e, ctx ->
            when (e) {
                is TokenExpiredException -> log.info("AzureAD-token expired on {}", e.expiredOn)
                else -> log.error("Unexpected exception {} while authenticating AzureAD-token", e::class.simpleName, e)
            }
            ctx.status(HttpStatus.UNAUTHORIZED).result("")
        }.exception(SigningKeyNotFoundException::class.java) { _, ctx ->
            log.warn("Noen prøvde å aksessere endepunkt med en token signert med en falsk issuer")
            ctx.status(HttpStatus.UNAUTHORIZED).result("")
        }.exception(MissingClaimException::class.java) { e, ctx ->
            if(e.claimName=="groups") {
                ctx.status(HttpStatus.FORBIDDEN).result("")
            } else {
                log.warn("Noen prøvde å aksessere endepunkt med en token med manglende claim", e)
                ctx.status(HttpStatus.UNAUTHORIZED).result("")
            }
        }
}

private fun verifyJwt(
    verifiers: List<JWTVerifier>,
    token: String
): DecodedJWT {
    for (verifier in verifiers) {
        try {
            return verifier.verify(token)
        } catch (e: SigningKeyNotFoundException) {
            // Token ikke utstedt for denne verifieren, prøv neste
        } catch (e: JWTVerificationException) {
            throw e
        }
    }

    throw SigningKeyNotFoundException("Token ikke signert av noen av issuerene", null)
}

/**
 * Henter token ut fra header fra en Context
 */
private fun Context.hentToken(): String {
    val authorizationHeader = header(HttpHeader.AUTHORIZATION.name)
        ?: run {
            log.error("Authorization header missing!")
            throw UnauthorizedResponse()
        }
    if (!authorizationHeader.startsWith("Bearer")) {
        log.error("Authorization header not with 'Bearer ' prefix!")
        throw UnauthorizedResponse()
    }
    return authorizationHeader.removePrefix("Bearer ")
}

/**
 * Setter opp en jwtVerifier som verifiserer token
 */
private fun jwtVerifier(
    authenticationConfiguration: AuthenticationConfiguration,
): JWTVerifier = JWT.require(algorithm(authenticationConfiguration.jwksUri))
    .withIssuer(authenticationConfiguration.issuer)
    .withAudience(authenticationConfiguration.audience)
    .withClaimPresence(navIdentClaim)
    .withClaimPresence("groups")
    .build()

/**
 * Setter opp algoritmen som tokenet skal være signert under
 */
private fun algorithm(azureOpenidConfigJwksUri: String): Algorithm {
    val jwkProvider = jwkProvider(azureOpenidConfigJwksUri)
    return Algorithm.RSA256(object : RSAKeyProvider {
        override fun getPublicKeyById(keyId: String) = jwkProvider.get(keyId).publicKey as RSAPublicKey
        override fun getPrivateKey() = throw IllegalStateException()
        override fun getPrivateKeyId() = throw IllegalStateException()
    })
}

/**
 * Setter opp Jwk-nøkkel for token-verifikasjon
 */
private fun jwkProvider(azureOpenidConfigJwksUri: String) =
    JwkProviderBuilder(URI(azureOpenidConfigJwksUri).toURL())
        .cached(10, 1, TimeUnit.HOURS)
        .build()