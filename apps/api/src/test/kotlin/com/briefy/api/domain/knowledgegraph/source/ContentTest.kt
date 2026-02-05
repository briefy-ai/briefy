package com.briefy.api.domain.knowledgegraph.source

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ContentTest {

    @Test
    fun `countWords returns correct count for normal text`() {
        assertEquals(5, Content.countWords("The quick brown fox jumps"))
    }

    @Test
    fun `countWords handles multiple spaces`() {
        assertEquals(3, Content.countWords("hello   world   test"))
    }

    @Test
    fun `countWords handles tabs and newlines`() {
        assertEquals(3, Content.countWords("hello\tworld\ntest"))
    }

    @Test
    fun `countWords returns zero for empty string`() {
        assertEquals(0, Content.countWords(""))
    }

    @Test
    fun `countWords returns zero for blank string`() {
        assertEquals(0, Content.countWords("   "))
    }

    @Test
    fun `countWords handles single word`() {
        assertEquals(1, Content.countWords("hello"))
    }

    @Test
    fun `countWords handles leading and trailing whitespace`() {
        assertEquals(3, Content.countWords("  hello world test  "))
    }

    @Test
    fun `from creates Content with correct word count`() {
        val content = Content.from("The quick brown fox jumps over the lazy dog")
        assertEquals("The quick brown fox jumps over the lazy dog", content.text)
        assertEquals(9, content.wordCount)
    }

    @Test
    fun `from creates Content with zero word count for empty text`() {
        val content = Content.from("")
        assertEquals("", content.text)
        assertEquals(0, content.wordCount)
    }
}
