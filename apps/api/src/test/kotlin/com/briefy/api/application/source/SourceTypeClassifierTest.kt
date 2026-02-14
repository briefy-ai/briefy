package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourceTypeClassifierTest {
    private val classifier = SourceTypeClassifier()

    @Test
    fun `classifies youtube as video`() {
        assertEquals(SourceType.VIDEO, classifier.classify("https://youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(SourceType.VIDEO, classifier.classify("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `classifies unknown as blog`() {
        assertEquals(SourceType.BLOG, classifier.classify("https://example.com/some-post"))
    }
}
