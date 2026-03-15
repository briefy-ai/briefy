package com.briefy.api.application.source

import com.briefy.api.infrastructure.tts.MarkdownStripper
import java.security.MessageDigest
import java.util.HexFormat

object NarrationContentHashing {
    private val markdownStripper = MarkdownStripper()

    fun hash(contentText: String): String {
        val plainText = markdownStripper.strip(contentText)
        val digest = MessageDigest.getInstance("SHA-256").digest(plainText.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
