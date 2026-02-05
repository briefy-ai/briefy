package com.briefy.api.infrastructure.extraction

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Component
class JsoupContentExtractor : ContentExtractor {

    companion object {
        private const val TIMEOUT_MS = 10_000
        private val USER_AGENT = "Mozilla/5.0 (compatible; Briefy/1.0; +https://briefy.ai)"

        private val DATE_FORMATTERS = listOf(
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_DATE
        )
    }

    override fun extract(url: String): ExtractionResult {
        val document = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)
            .followRedirects(true)
            .get()

        return ExtractionResult(
            text = extractContent(document),
            title = extractTitle(document),
            author = extractAuthor(document),
            publishedDate = extractPublishedDate(document)
        )
    }

    private fun extractTitle(doc: Document): String? {
        // Try Open Graph title first
        doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }?.let { return it }

        // Try Twitter card title
        doc.selectFirst("meta[name=twitter:title]")?.attr("content")?.takeIf { it.isNotBlank() }?.let { return it }

        // Fall back to <title> tag
        return doc.title().takeIf { it.isNotBlank() }
    }

    private fun extractAuthor(doc: Document): String? {
        // Try meta author tag
        doc.selectFirst("meta[name=author]")?.attr("content")?.takeIf { it.isNotBlank() }?.let { return it }

        // Try Open Graph article author
        doc.selectFirst("meta[property=article:author]")?.attr("content")?.takeIf { it.isNotBlank() }?.let { return it }

        // Try Twitter creator
        doc.selectFirst("meta[name=twitter:creator]")?.attr("content")?.takeIf { it.isNotBlank() }?.let { return it }

        // Try rel=author link
        doc.selectFirst("[rel=author]")?.text()?.takeIf { it.isNotBlank() }?.let { return it }

        // Try schema.org author
        doc.selectFirst("[itemprop=author]")?.text()?.takeIf { it.isNotBlank() }?.let { return it }

        return null
    }

    private fun extractPublishedDate(doc: Document): Instant? {
        // Try various date meta tags
        val dateSelectors = listOf(
            "meta[property=article:published_time]",
            "meta[name=publish-date]",
            "meta[name=date]",
            "meta[property=og:updated_time]",
            "time[datetime]",
            "[itemprop=datePublished]"
        )

        for (selector in dateSelectors) {
            val element = doc.selectFirst(selector) ?: continue
            val dateStr = if (element.tagName() == "time") {
                element.attr("datetime")
            } else if (element.hasAttr("content")) {
                element.attr("content")
            } else {
                element.text()
            }

            if (dateStr.isNotBlank()) {
                parseDate(dateStr)?.let { return it }
            }
        }

        return null
    }

    private fun parseDate(dateStr: String): Instant? {
        for (formatter in DATE_FORMATTERS) {
            try {
                return formatter.parse(dateStr, Instant::from)
            } catch (e: DateTimeParseException) {
                // Try next formatter
            }
        }

        // Try parsing as LocalDate and convert to Instant
        try {
            val localDate = java.time.LocalDate.parse(dateStr.substring(0, 10))
            return localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        } catch (e: Exception) {
            // Ignore
        }

        return null
    }

    private fun extractContent(doc: Document): String {
        // Remove unwanted elements
        doc.select("script, style, nav, header, footer, aside, .ad, .advertisement, .sidebar, .menu, .navigation, .comment, .comments, #comments").remove()

        // Try to find main content area
        val contentElement = doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst("[role=main]")
            ?: doc.selectFirst(".post-content")
            ?: doc.selectFirst(".article-content")
            ?: doc.selectFirst(".entry-content")
            ?: doc.selectFirst(".content")
            ?: doc.body()

        // Extract text and clean up whitespace
        return contentElement?.text()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?: ""
    }
}
