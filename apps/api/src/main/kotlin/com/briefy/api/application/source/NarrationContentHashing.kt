package com.briefy.api.application.source

import com.briefy.api.infrastructure.tts.MarkdownStripper
import java.security.MessageDigest
import java.util.HexFormat

object NarrationContentHashing {
    private val markdownStripper = MarkdownStripper()

    fun hash(contentText: String): String {
        val plainText = markdownStripper.strip(contentText)
        return hashExact(plainText)
    }

    fun lookupHashes(contentText: String): List<String> {
        return listOf(hash(contentText), legacyHash(contentText)).distinct()
    }

    private fun legacyHash(contentText: String): String {
        return hashExact(contentText)
    }

    private fun hashExact(contentText: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(contentText.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
