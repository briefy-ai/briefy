package com.briefy.api.infrastructure.tts

import kotlin.test.Test
import kotlin.test.assertEquals

class NarrationLanguageResolverTest {
    private val resolver = NarrationLanguageResolver(NarrationScriptPreparer(MarkdownStripper()))

    @Test
    fun `uses normalized transcript language when available`() {
        assertEquals("es", resolver.resolve("es-ES", "This text should be ignored"))
    }

    @Test
    fun `detects spanish from extracted text`() {
        val text = "Hola, esta es una narracion breve para una fuente en espanol."
        assertEquals("es", resolver.resolve(null, text))
    }

    @Test
    fun `defaults to english when no stronger signal exists`() {
        val text = "This is a short source narration for an English article."
        assertEquals("en", resolver.resolve(null, text))
    }
}
