package no.nav.toi

import java.util.*

enum class Rolle {
    MODIA_GENERELL,
    MODIA_OPPFØLGING,
    REKBIS_ARBEIDSGIVERRETTET,
    REKBIS_JOBBSØKERRETTET,
    REKBIS_UTVIKLER,
}

/*
    Holder på UUID-ene som brukes for å identifisere roller i Azure AD.
    Det er ulik spesifikasjon for dev og prod.
 */
data class RolleUuidSpesifikasjon(
    private val modiaGenerell: UUID,
    private val modiaOppfølging: UUID,
    private val rekbisUtvikler: UUID,
    private val rekbisArbeidsgiverrettet: UUID,
    private val rekbisJobbsøkerrettet: UUID,
) {
    private fun rolleForUuid(uuid: UUID): Rolle? {
        return when (uuid) {
            modiaGenerell -> Rolle.MODIA_GENERELL
            modiaOppfølging -> Rolle.MODIA_OPPFØLGING
            rekbisUtvikler -> Rolle.REKBIS_UTVIKLER
            rekbisArbeidsgiverrettet -> Rolle.REKBIS_ARBEIDSGIVERRETTET
            rekbisJobbsøkerrettet -> Rolle.REKBIS_JOBBSØKERRETTET
            else -> { log.warn("Ukjent rolle-UUID: $uuid"); null }
        }
    }

    fun rollerForUuider(uuider: Collection<UUID>): Set<Rolle> =
        EnumSet.copyOf(uuider.mapNotNull { rolleForUuid(it) })
}

