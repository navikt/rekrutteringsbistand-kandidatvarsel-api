package no.nav.toi.kandidatvarsel

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.MissingClaimException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.RSAKeyProvider
import io.javalin.Javalin
import io.javalin.http.*
import io.javalin.security.RouteRole
import no.nav.toi.kandidatvarsel.Rolle.UNPROTECTED
import org.eclipse.jetty.http.HttpHeader
import java.lang.System.getenv
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.*
import java.util.concurrent.TimeUnit

enum class Rolle: RouteRole {
    REKBIS_ARBEIDSGIVERRETTET,
    REKBIS_JOBBSØKERRETTET,
    REKBIS_UTVIKLER,
    UNPROTECTED,
}

data class Issuer(
    val issuer: String,
    val jwksUri: String,
    val audience: String,
) {
    /** Setter opp en jwtVerifier som verifiserer token */
    fun verifier(): JWTVerifier {
        val jwkProvider = JwkProviderBuilder(URI(jwksUri).toURL())
            .cached(10, 1, TimeUnit.HOURS)
            .build()

        val algorithm = Algorithm.RSA256(object : RSAKeyProvider {
            override fun getPublicKeyById(keyId: String) = jwkProvider.get(keyId).publicKey as RSAPublicKey
            override fun getPrivateKey() = throw IllegalStateException()
            override fun getPrivateKeyId() = throw IllegalStateException()
        })

        return JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
    }
}


class AzureAdConfig(
    private val issuers: List<Issuer>,
    val authorizedPartyNames: List<String>,

    private val rekbisUtvikler: UUID,
    private val rekbisArbeidsgiverrettet: UUID,
    private val rekbisJobbsøkerrettet: UUID,
) {
    private val verifiers = issuers.map { it.verifier() }

    fun verify(token: String): DecodedJWT {
        for (verifier in verifiers) {
            try {
                return verifier.verify(token)
            } catch (e: SigningKeyNotFoundException) {
                // Token ikke utstedt for denne verifieren, prøv neste
            }
        }
        throw UnauthorizedResponse("Token not issued for any of the configured issuers")
    }

    private fun rolleForUuid(uuid: UUID): Rolle? {
        return when (uuid) {
            rekbisUtvikler -> Rolle.REKBIS_UTVIKLER
            rekbisArbeidsgiverrettet -> Rolle.REKBIS_ARBEIDSGIVERRETTET
            rekbisJobbsøkerrettet -> Rolle.REKBIS_JOBBSØKERRETTET
            else -> { log.warn("Ukjent rolle-UUID: $uuid"); null }
        }
    }

    fun rollerForUuider(uuider: Collection<UUID>): Set<Rolle> =
        uuider.mapNotNull { rolleForUuid(it) }.toSet()

    companion object {
        fun nais() = AzureAdConfig(
            issuers = listOfNotNull(
                Issuer(
                    audience = getenv("AZURE_APP_CLIENT_ID"),
                    issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
                    jwksUri  = getenv("AZURE_OPENID_CONFIG_JWKS_URI"),
                ),
                if (getenv("NAIS_CLUSTER_NAME") == "dev-gcp")
                    Issuer(
                        audience = getenv("FAKEDINGS_APP_CLIENT_ID"),
                        issuer = getenv("FAKEDINGS_OPENID_CONFIG_ISSUER"),
                        jwksUri = getenv("FAKEDINGS_OPENID_CONFIG_JWKS_URI"),
                    )
                else null
            ),
            authorizedPartyNames = getenvOrThrow("AUTHORIZED_PARTY_NAMES").split(","),
            rekbisUtvikler = getUuid("AD_GROUP_REKBIS_UTVIKLER"),
            rekbisArbeidsgiverrettet = getUuid("AD_GROUP_REKBIS_ARBEIDSGIVERRETTET"),
            rekbisJobbsøkerrettet = getUuid("AD_GROUP_REKBIS_JOBBSOKERRETTET"),
        )

        private fun getUuid(name: String) = UUID.fromString(getenvOrThrow(name))
    }
}

interface Principal {
    fun mayAccess(routeRoles: Set<RouteRole>): Boolean
}

/** Representerer en autensiert nav-ansatt */
data class UserPrincipal(
    val navident: String,
    val roller: Set<Rolle>,
): Principal {
    override fun mayAccess(routeRoles: Set<RouteRole>) =
        UNPROTECTED in routeRoles || roller.any { it in routeRoles }

    companion object {
        fun fromClaims(claims: Map<String, Claim>, azureAdConfig: AzureAdConfig): UserPrincipal {
            val navIdent = claims["NAVident"]?.asString() ?:
                throw MissingClaimException("NAVident")

            val groups = claims["groups"]?.asList(UUID::class.java) ?:
                throw UnauthorizedResponse("groups")

            return UserPrincipal(
                navident = navIdent,
                roller = azureAdConfig.rollerForUuider(groups)
            )
        }
    }
}

/**
 * Henter ut en autensiert bruker fra en kontekst. Kaster InternalServerErrorResponse om det ikke finnes en autensiert bruker
 */
fun Context.authenticatedUser() = attribute<UserPrincipal>("principal")
    ?: run {
        log.error("No authenticated user found!")
        throw InternalServerErrorResponse()
    }

/**
 * Setter opp token-verifisering på en path på Javalin-serveren
 */
fun Javalin.azureAdAuthentication(azureAdConfig: AzureAdConfig): Javalin {
    return beforeMatched { ctx ->
        val token = ctx.hentToken() ?: run {
            if (UNPROTECTED in ctx.routeRoles()) {
                return@beforeMatched
            }
            log.error("Kall mot {} uten token", ctx.path())
            throw UnauthorizedResponse()
        }

        val claims = azureAdConfig.verify(token).claims

        val principal = UserPrincipal.fromClaims(claims, azureAdConfig)

        if (!principal.mayAccess(ctx.routeRoles())) {
            secureLog.error("principal=${principal} tried to access ${ctx.path()}, but is not authorized. Must have at least one of ${ctx.routeRoles()}")
            log.error("principal tried to access ${ctx.path()}, but does not have any required role ${ctx.routeRoles()}. See secure log for principal.")
            throw ForbiddenResponse()
        }

        ctx.attribute("principal", principal)
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
            if (e.claimName == "groups") {
                ctx.status(HttpStatus.FORBIDDEN).result("")
            } else {
                log.warn("Noen prøvde å aksessere endepunkt med en token med manglende claim", e)
                ctx.status(HttpStatus.UNAUTHORIZED).result("")
            }
        }
}


/**
 * Henter token ut fra header fra en Context
 */
fun Context.hentToken(): String? {
    val authorizationHeader = header(HttpHeader.AUTHORIZATION.name)
        ?: return null

    if (!authorizationHeader.startsWith("Bearer ")) {
        log.error("Authorization header not with 'Bearer ' prefix!")
        throw UnauthorizedResponse()
    }

    return authorizationHeader.removePrefix("Bearer ")
}

