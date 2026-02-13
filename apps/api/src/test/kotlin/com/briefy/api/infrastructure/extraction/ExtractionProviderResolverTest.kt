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
    private val xApiProvider: ExtractionProvider = mock()

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
        whenever(settingsService.isXApiEnabled(userId)).thenReturn(false)
        whenever(factory.jsoup()).thenReturn(jsoupProvider)

        val provider = resolver.resolveProvider(userId, "twitter")

        assertSame(jsoupProvider, provider)
    }

    @Test
    fun `returns x api when x platform and configured`() {
        whenever(settingsService.isXApiEnabled(userId)).thenReturn(true)
        whenever(settingsService.getXApiBearerToken(userId)).thenReturn("x-token")
        whenever(factory.xApi("x-token")).thenReturn(xApiProvider)

        val provider = resolver.resolveProvider(userId, "x")

        assertSame(xApiProvider, provider)
    }

    @Test
    fun `returns jsoup when firecrawl disabled`() {
        whenever(settingsService.isFirecrawlEnabled(userId)).thenReturn(false)
        whenever(factory.jsoup()).thenReturn(jsoupProvider)

        val provider = resolver.resolveProvider(userId, "web")

        assertSame(jsoupProvider, provider)
    }
}
