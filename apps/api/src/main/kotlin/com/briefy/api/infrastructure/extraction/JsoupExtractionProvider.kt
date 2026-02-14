package com.briefy.api.infrastructure.extraction

import org.jsoup.Jsoup
import org.jsoup.Connection
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
        private const val ACCEPT_HEADER = "text/markdown, text/html;q=0.9, application/xhtml+xml;q=0.8"
        private const val ACCEPT_ENCODING_HEADER = "identity"
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

            val response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", ACCEPT_HEADER)
                .header("Accept-Encoding", ACCEPT_ENCODING_HEADER)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .execute()
            val finalUrl = response.url().toString()
            validateFetchableUrl(finalUrl)

            val result = if (isMarkdownResponse(response)) {
                extractFromMarkdown(response)
            } else {
                extractFromHtml(response.parse())
            }
            val sanitizedResult = sanitizeForPersistence(result, url, finalUrl)

            if (sanitizedResult.text.isBlank()) {
                logger.warn("[extractor:jsoup] extraction_empty_content url={} finalUrl={}", url, finalUrl)
            }
            logger.info(
                "[extractor:jsoup] extraction_succeeded url={} finalUrl={} contentType={} textLength={}",
                url,
                finalUrl,
                response.contentType(),
                sanitizedResult.text.length
            )
            sanitizedResult
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

    private fun extractFromHtml(doc: Document): ExtractionResult {
        return ExtractionResult(
            text = extractContent(doc),
            title = extractTitle(doc),
            author = extractAuthor(doc),
            publishedDate = extractPublishedDate(doc),
            aiFormatted = false
        )
    }

    private fun extractFromMarkdown(response: Connection.Response): ExtractionResult {
        val markdown = response.body().trim()
        val frontmatter = parseFrontmatter(markdown)
        val title = frontmatter["title"] ?: extractFirstMarkdownHeading(markdown)
        val author = frontmatter["author"]
        val publishedDate = frontmatter["date"]?.let { parseDate(it) }
            ?: frontmatter["published"]?.let { parseDate(it) }
            ?: frontmatter["publishedDate"]?.let { parseDate(it) }

        return ExtractionResult(
            text = markdown,
            title = title,
            author = author,
            publishedDate = publishedDate,
            aiFormatted = false
        )
    }

    private fun sanitizeForPersistence(result: ExtractionResult, originalUrl: String, finalUrl: String): ExtractionResult {
        val sanitizedText = stripUnsupportedChars(result.text)
        val sanitizedTitle = result.title?.let(::stripUnsupportedChars)
        val sanitizedAuthor = result.author?.let(::stripUnsupportedChars)

        if (sanitizedText.length != result.text.length) {
            logger.warn(
                "[extractor:jsoup] removed_unsupported_chars url={} finalUrl={} removed={}",
                originalUrl,
                finalUrl,
                result.text.length - sanitizedText.length
            )
        }

        if (sanitizedText === result.text &&
            sanitizedTitle == result.title &&
            sanitizedAuthor == result.author
        ) {
            return result
        }

        return result.copy(
            text = sanitizedText,
            title = sanitizedTitle,
            author = sanitizedAuthor
        )
    }

    private fun stripUnsupportedChars(value: String): String {
        return value.filterNot { ch ->
            ch == '\u0000' || (ch.isISOControl() && ch != '\n' && ch != '\r' && ch != '\t')
        }
    }

    private fun isMarkdownResponse(response: Connection.Response): Boolean {
        return response.contentType()
            ?.lowercase()
            ?.startsWith("text/markdown") == true
    }

    private fun parseFrontmatter(markdown: String): Map<String, String> {
        val lines = markdown.lines()
        if (lines.isEmpty() || lines.first().trim() != "---") {
            return emptyMap()
        }

        val metadata = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line == "---") {
                return metadata
            }
            if (line.isBlank() || line.startsWith("#")) {
                continue
            }
            val separatorIdx = line.indexOf(':')
            if (separatorIdx <= 0 || separatorIdx >= line.lastIndex) {
                continue
            }
            val key = line.substring(0, separatorIdx).trim()
            val value = line.substring(separatorIdx + 1).trim().removeSurrounding("\"")
            if (key.isNotBlank() && value.isNotBlank()) {
                metadata[key] = value
            }
        }

        return emptyMap()
    }

    private fun extractFirstMarkdownHeading(markdown: String): String? {
        return markdown
            .lineSequence()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
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
