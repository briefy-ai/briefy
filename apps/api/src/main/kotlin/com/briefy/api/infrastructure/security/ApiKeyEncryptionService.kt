package com.briefy.api.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class ApiKeyEncryptionService(
    @Value("\${security.encryption-key}") encryptionKey: String
) {
    private val secretKey = SecretKeySpec(parseBase64Key(encryptionKey), "AES")
    private val random = SecureRandom()

    fun encrypt(plainText: String): String {
        require(plainText.isNotBlank()) { "API key must not be blank" }

        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val payload = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(cipherText, 0, payload, iv.size, cipherText.size)

        return Base64.getEncoder().encodeToString(payload)
    }

    fun decrypt(cipherTextBase64: String): String {
        require(cipherTextBase64.isNotBlank()) { "Encrypted API key must not be blank" }

        val payload = try {
            Base64.getDecoder().decode(cipherTextBase64)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Encrypted API key is not valid base64")
        }

        require(payload.size > IV_LENGTH) { "Encrypted API key payload is invalid" }

        val iv = payload.copyOfRange(0, IV_LENGTH)
        val cipherText = payload.copyOfRange(IV_LENGTH, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val plainBytes = cipher.doFinal(cipherText)
        return plainBytes.toString(Charsets.UTF_8)
    }

    private fun parseBase64Key(base64Key: String): ByteArray {
        val key = try {
            Base64.getDecoder().decode(base64Key)
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("security.encryption-key must be valid base64")
        }

        require(key.size == KEY_SIZE_BYTES) {
            "security.encryption-key must decode to exactly $KEY_SIZE_BYTES bytes"
        }

        return key
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BYTES = 32
    }
}
