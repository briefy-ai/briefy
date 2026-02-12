package com.briefy.api.infrastructure.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApiKeyEncryptionServiceTest {
    private val base64Key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="

    @Test
    fun `encrypt and decrypt roundtrip preserves value`() {
        val service = ApiKeyEncryptionService(base64Key)
        val plain = "fc-secret-key"

        val encrypted = service.encrypt(plain)
        val decrypted = service.decrypt(encrypted)

        assertNotEquals(plain, encrypted)
        assertEquals(plain, decrypted)
    }

    @Test
    fun `invalid encryption key fails fast`() {
        assertThrows<IllegalStateException> {
            ApiKeyEncryptionService("not-base64")
        }
    }
}
