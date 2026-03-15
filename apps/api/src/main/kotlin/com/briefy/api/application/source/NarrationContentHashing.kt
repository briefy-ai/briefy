package com.briefy.api.application.source

import java.security.MessageDigest
import java.util.HexFormat

object NarrationContentHashing {
    fun hash(contentText: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(contentText.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
