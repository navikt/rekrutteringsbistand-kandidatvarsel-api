package no.nav.toi.kandidatvarsel

import no.nav.common.audit_log.cef.AuthorizationDecision
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.cef.CefMessageEvent
import no.nav.common.audit_log.log.AuditLogger
import no.nav.common.audit_log.log.AuditLoggerImpl
import org.slf4j.LoggerFactory

val secureLog = LoggerFactory.getLogger("secureLog")!!

object AuditLogg {
    private val auditLogger: AuditLogger = AuditLoggerImpl()

    fun logCefMessage(navIdent: String, userid: String, msg: String, tilgang: Boolean) {
        val message = CefMessage.builder()
            .applicationName("Rekrutteringsbistand")
            .loggerName("rekrutteringsbistand-kandidatvarsel-api")
            .event(CefMessageEvent.ACCESS)
            .name("Sporingslogg")
            .authorizationDecision(if(tilgang) AuthorizationDecision.PERMIT else AuthorizationDecision.DENY)
            .sourceUserId(navIdent)
            .destinationUserId(userid)
            .timeEnded(System.currentTimeMillis())
            .extension("msg", msg)
            .build()
        val ekstraSpaceSidenAuditloggerInnimellomKutterSisteTegn = " "
        auditLogger.log("$message" + ekstraSpaceSidenAuditloggerInnimellomKutterSisteTegn)
        secureLog.info("auditlogger: {}", "$message" + ekstraSpaceSidenAuditloggerInnimellomKutterSisteTegn)
    }
}