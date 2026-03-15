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

    @Test
    fun `strip removes tilde fenced code blocks`() {
        val result = stripper.strip(
            """
            Intro

            ~~~python
            print("hidden")
            ~~~

            Outro
            """.trimIndent()
        )

        assertEquals("Intro Outro", result)
    }

    @Test
    fun `strip does not treat fence markers with trailing text as a closing fence`() {
        val result = stripper.strip(
            """
            Intro

            ```kotlin
            val snippet = "```json"
            ```

            Outro
            """.trimIndent()
        )

        assertEquals("Intro Outro", result)
    }

    @Test
    fun `strip keeps markdown link text when url contains parentheses`() {
        val result = stripper.strip(
            "See [the docs](https://example.com/path_(with_parentheses)) for details."
        )

        assertEquals("See the docs for details.", result)
    }
}
