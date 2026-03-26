package com.briefy.api.infrastructure.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NarrationScriptPreparerTest {
    private val preparer = NarrationScriptPreparer(MarkdownStripper())

    @Test
    fun `prepare replaces fenced code blocks and preserves surrounding prose`() {
        val result = preparer.prepare(
            """
            Intro

            ```kotlin
            println("hidden")
            ```

            Outro
            """.trimIndent()
        )

        assertEquals("Intro Code example skipped for audio clarity. Outro", result)
    }

    @Test
    fun `prepare replaces markdown tables`() {
        val result = preparer.prepare(
            """
            | Term | Docs |
            | --- | --- |
            | cat | D0, D2 |
            | light | D0, D1 |
            """.trimIndent()
        )

        assertEquals("Table skipped for audio clarity.", result)
    }

    @Test
    fun `prepare replaces dense structured blocks`() {
        val result = preparer.prepare(
            """
            across -> D0, D1, D2
            and -> D2, D3
            bat -> D3
            black -> D2
            """.trimIndent()
        )

        assertEquals("Dense structured example skipped for audio clarity.", result)
    }

    @Test
    fun `prepare preserves short prose lists`() {
        val result = preparer.prepare(
            """
            Key ideas:

            - search engines use inverted indexes
            - posting lists support intersections
            - relevance comes later
            """.trimIndent()
        )

        assertEquals(
            "Key ideas: search engines use inverted indexes posting lists support intersections relevance comes later",
            result
        )
    }

    @Test
    fun `prepare collapses consecutive skipped blocks with the same annotation`() {
        val result = preparer.prepare(
            """
            ```python
            print("one")
            ```

            ```python
            print("two")
            ```
            """.trimIndent()
        )

        assertEquals("Code example skipped for audio clarity.", result)
    }

    @Test
    fun `prepare localizes skip annotations for spanish narration`() {
        val result = preparer.prepare(
            """
            Intro

            ```python
            print("hidden")
            ```
            """.trimIndent(),
            "es"
        )

        assertEquals("Intro Se omitio un ejemplo de codigo para mayor claridad del audio.", result)
    }

    @Test
    fun `prepare preserves prose around markdown tables without blank separators`() {
        val result = preparer.prepare(
            """
            Overview:
            | Term | Docs |
            | --- | --- |
            | cat | D0 |
            Summary follows
            """.trimIndent()
        )

        assertEquals("Overview: Table skipped for audio clarity. Summary follows", result)
    }

    @Test
    fun `prepare does not treat prose lines with standalone numbers as dense structured content`() {
        val result = preparer.prepare(
            """
            Publication appeared during 2024 revisions overall
            Researchers documented 42 outcomes across teams
            Analysts reviewed 7 scenarios for reliability
            Editors retained 3 narrative sections afterward
            """.trimIndent()
        )

        assertEquals(
            "Publication appeared during 2024 revisions overall Researchers documented 42 outcomes across teams Analysts reviewed 7 scenarios for reliability Editors retained 3 narrative sections afterward",
            result
        )
    }
}
