package no.nav.toi.kuberneteshealth

import io.javalin.Javalin

private const val endepunktReady = "/internal/ready"
private const val endepunktAlive = "/internal/alive"

fun Javalin.handleHealth() {
    get(endepunktReady) { ctx->
        ctx.result("isReady")
    }
    get(endepunktAlive) { ctx->
        ctx.result("isAlive")
    }
}