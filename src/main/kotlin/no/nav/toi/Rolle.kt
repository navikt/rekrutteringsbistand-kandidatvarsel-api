package no.nav.toi

import java.util.*

enum class Rolle {
    MODIA_GENERELL,
    MODIA_OPPFØLGING,
}

/*
    Holder på UUID-ene som brukes for å identifisere roller i Azure AD.
    Det er ulik spesifikasjon for dev og prod.
 */
data class RolleUuidSpesifikasjon(
    private val modiaGenerell: UUID,
    private val modiaOppfølging: UUID,
) {
    private fun rolleForUuid(uuid: UUID): Rolle? {
        return when (uuid) {
            modiaGenerell -> Rolle.MODIA_GENERELL
            modiaOppfølging -> Rolle.MODIA_OPPFØLGING
            else -> { log.warn("Ukjent rolle-UUID: $uuid"); null }
        }
    }

    fun rollerForUuider(uuider: Collection<UUID>): Set<Rolle> =
        EnumSet.copyOf(uuider.mapNotNull { rolleForUuid(it) })
}

