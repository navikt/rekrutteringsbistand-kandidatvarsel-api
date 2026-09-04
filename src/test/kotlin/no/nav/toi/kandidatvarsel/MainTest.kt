package no.nav.toi.kandidatvarsel

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainTest {

    @Test
    fun `workop-lyttere er deaktivert i prod og ukjente miljoer`() {
        assertFalse(skalRegistrereWorkOpLyttere("prod-gcp"))
        assertFalse(skalRegistrereWorkOpLyttere("annet-miljø"))
    }

    @Test
    fun `workop-lyttere er aktivert i dev og lokalt`() {
        assertTrue(skalRegistrereWorkOpLyttere("dev-gcp"))
        assertTrue(skalRegistrereWorkOpLyttere("local"))
        assertTrue(skalRegistrereWorkOpLyttere(null))
    }
}
