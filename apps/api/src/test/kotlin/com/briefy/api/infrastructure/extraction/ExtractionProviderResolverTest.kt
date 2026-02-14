package com.briefy.api.infrastructure.extraction

import com.briefy.api.application.settings.UserSettingsService
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class ExtractionProviderResolverTest {
    private val factory: ExtractionProviderFactory = mock()
    private val settingsService: UserSettingsService = mock()
    private val resolver = ExtractionProviderResolver(factory, settingsService)
    private val userId = UUID.randomUUID()
    private val jsoupProvider: ExtractionProvider = mock()
    private val firecrawlProvider: ExtractionProvider = mock()
    private val youtubeProvider: ExtractionProvider = mock()

    @Test
    fun `returns youtube provider for youtube platform`() {
        whenever(factory.youtube()).thenReturn(youtubeProvider)

        val provider = resolver.resolveProvider(userId, "youtube")

        assertSame(youtubeProvider, provider)
    }

    @Test
    fun `returns firecrawl when platform supported and configured`() {
        whenever(settingsService.isFirecrawlEnabled(userId)).thenReturn(true)
        whenever(settingsService.getFirecrawlApiKey(userId)).thenReturn("fc-key")
        whenever(factory.firecrawl("fc-key")).thenReturn(firecrawlProvider)

        val provider = resolver.resolveProvider(userId, "web")

        assertSame(firecrawlProvider, provider)
    }

    @Test
    fun `returns jsoup for excluded platform`() {
        whenever(factory.jsoup()).thenReturn(jsoupProvider)

        val provider = resolver.resolveProvider(userId, "twitter")

        assertSame(jsoupProvider, provider)
    }

    @Test
    fun `returns jsoup when firecrawl disabled`() {
        whenever(settingsService.isFirecrawlEnabled(userId)).thenReturn(false)
        whenever(factory.jsoup()).thenReturn(jsoupProvider)

        val provider = resolver.resolveProvider(userId, "web")

        assertSame(jsoupProvider, provider)
    }
}
