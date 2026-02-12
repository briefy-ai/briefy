package com.briefy.api.infrastructure.extraction

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Component
class JsoupExtractionProvider : ExtractionProvider {
    private val logger = LoggerFactory.getLogger(JsoupExtractionProvider::class.java)

    companion object {
        private const val TIMEOUT_MS = 10_000
        private val USER_AGENT = "Mozilla/5.0 (compatible; Briefy/1.0; +https://briefy.ai)"

        private val DATE_FORMATTERS = listOf(
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_DATE
        )
    }

    override val id: ExtractionProviderId = ExtractionProviderId.JSOUP

    override fun extract(url: String): ExtractionResult {
        logger.info("[extractor:jsoup] extraction_started url={}", url)
        return try {
            validateFetchableUrl(url)

            val document = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()
            validateFetchableUrl(document.location())

            val extractedText = extractContent(document)
            if (extractedText.isBlank()) {
                logger.warn("[extractor:jsoup] extraction_empty_content url={} finalUrl={}", url, document.location())
            }

            val result = ExtractionResult(
                text = extractedText,
                title = extractTitle(document),
                author = extractAuthor(document),
                publishedDate = extractPublishedDate(document),
                aiFormatted = false
            )
            logger.info(
                "[extractor:jsoup] extraction_succeeded url={} finalUrl={} textLength={}",
                url,
                document.location(),
                result.text.length
            )
            result
        } catch (e: IllegalArgumentException) {
            logger.warn("[extractor:jsoup] extraction_blocked url={} reason={}", url, e.message)
            throw e
        } catch (e: Exception) {
            logger.error("[extractor:jsoup] extraction_failed url={}", url, e)
            throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.UNKNOWN,
                message = "Jsoup extraction failed for URL: $url",
                cause = e
            )
        }
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

    private fun validateFetchableUrl(url: String) {
        val uri = URI.create(url)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "Unsupported URL scheme" }

        val host = uri.host ?: throw IllegalArgumentException("Invalid URL host")
        require(!host.equals("localhost", ignoreCase = true)) { "Host is not allowed" }

        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty()) { "Host resolution failed" }
        for (address in addresses) {
            require(!isBlockedAddress(address)) { "Host resolves to a private or unsafe network" }
        }
    }

    private fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isSiteLocalAddress ||
            address.isLinkLocalAddress || address.isMulticastAddress
        ) {
            return true
        }

        val bytes = address.address
        if (address is Inet4Address) {
            // Carrier-grade NAT: 100.64.0.0/10
            if (bytes.size == 4) {
                val first = bytes[0].toInt() and 0xFF
                val second = bytes[1].toInt() and 0xFF
                if (first == 100 && second in 64..127) return true
            }
        } else if (bytes.isNotEmpty()) {
            // IPv6 unique local (fc00::/7) and link-local (fe80::/10).
            val first = bytes[0].toInt() and 0xFF
            val second = if (bytes.size > 1) bytes[1].toInt() and 0xFF else 0
            if ((first and 0xFE) == 0xFC) return true
            if (first == 0xFE && (second and 0xC0) == 0x80) return true
        }

        return false
    }
}
