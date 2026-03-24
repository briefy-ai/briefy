package com.briefy.api.application.source

import com.briefy.api.infrastructure.tts.MarkdownStripper
import com.briefy.api.infrastructure.tts.NarrationScriptPreparer
import java.security.MessageDigest
import java.util.HexFormat

object NarrationContentHashing {
    private val markdownStripper = MarkdownStripper()
    private val narrationScriptPreparer = NarrationScriptPreparer(markdownStripper)

    fun hash(contentText: String): String {
        return hashExact(narrationScriptPreparer.prepare(contentText))
    }

    fun lookupHashes(contentText: String): List<String> {
        return listOf(
            hash(contentText),
            strippedMarkdownHash(contentText),
            legacyHash(contentText)
        ).distinct()
    }

    private fun strippedMarkdownHash(contentText: String): String {
        return hashExact(markdownStripper.strip(contentText))
    }

    private fun legacyHash(contentText: String): String {
        return hashExact(contentText)
    }

    private fun hashExact(contentText: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(contentText.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
