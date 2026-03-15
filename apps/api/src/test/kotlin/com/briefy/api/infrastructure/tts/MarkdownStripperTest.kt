package com.briefy.api.infrastructure.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownStripperTest {
    private val stripper = MarkdownStripper()

    @Test
    fun `strip removes markdown syntax and code blocks`() {
        val result = stripper.strip(
            """
            # Heading
            
            This is **bold** and [linked](https://example.com).
            
            - item one
            - item two
            
            ```kotlin
            println("hidden")
            ```
            """.trimIndent()
        )

        assertEquals("Heading This is bold and linked. item one item two", result)
    }
}
