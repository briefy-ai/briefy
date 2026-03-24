package com.briefy.api.application.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.HexFormat

class NarrationContentHashingTest {
    @Test
    fun `lookupHashes returns prepared stripped and raw content hashes for structured content`() {
        val content = """
            # Inverted Indexes

            An inverted index maps terms to documents.

            across -> D0, D1, D2
            and -> D2, D3
            bat -> D3
            black -> D2

            Search for cat.
        """.trimIndent()

        val hashes = NarrationContentHashing.lookupHashes(content)

        assertEquals(3, hashes.size)
        assertTrue(
            hashes.contains(
                sha256("Inverted Indexes An inverted index maps terms to documents. Dense structured example skipped for audio clarity. Search for cat.")
            )
        )
        assertTrue(
            hashes.contains(
                sha256("Inverted Indexes An inverted index maps terms to documents. across -> D0, D1, D2 and -> D2, D3 bat -> D3 black -> D2 Search for cat.")
            )
        )
        assertTrue(hashes.contains(sha256(content)))
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
