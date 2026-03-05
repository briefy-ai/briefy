package com.briefy.api.application.briefing.tool

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.slf4j.LoggerFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.SocketTimeoutException

class HttpWebFetchProvider(
    private val timeoutMs: Int = 15_000,
    private val maxBodyBytes: Int = 512_000,
    private val ssrfCheckEnabled: Boolean = true
) : WebFetchTool {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(timeoutMs)
            setReadTimeout(timeoutMs)
        })
        .defaultHeader("User-Agent", "Briefy/1.0 (research assistant)")
        .defaultHeader("Accept", "text/html,application/xhtml+xml,text/plain,application/json")
        .build()

    override fun fetch(url: String): ToolResult<WebFetchResponse> {
        val validatedUri = if (ssrfCheckEnabled) {
            when (val check = SsrfGuard.validate(url)) {
                is ToolResult.Success -> check.data
                is ToolResult.Error -> return check
            }
        } else {
            java.net.URI.create(url)
        }

        return try {
            val body = restClient.get()
                .uri(validatedUri)
                .retrieve()
                .body(String::class.java)
                ?: return ToolResult.Error(ToolErrorCode.PARSE_ERROR, "Empty response body")

            if (body.length > maxBodyBytes) {
                return ToolResult.Error(
                    ToolErrorCode.CONTENT_TOO_LARGE,
                    "Response body ${body.length} bytes exceeds limit $maxBodyBytes"
                )
            }

            val extracted = extractReadableContent(body)
            ToolResult.Success(
                WebFetchResponse(
                    url = url,
                    title = extracted.first,
                    content = extracted.second,
                    contentLengthBytes = extracted.second.length
                )
            )
        } catch (e: RestClientResponseException) {
            mapHttpError(e, url)
        } catch (e: Exception) {
            mapException(e, url)
        }
    }

    private fun extractReadableContent(html: String): Pair<String?, String> {
        return try {
            val doc = Jsoup.parse(html)
            val title = doc.title().takeIf { it.isNotBlank() }

            // Remove non-content elements
            doc.select("script, style, nav, footer, header, aside, iframe, noscript, svg, form").remove()

            val mainContent = doc.select("article, main, [role=main]").firstOrNull() ?: doc.body()
            val text = mainContent?.let { Jsoup.clean(it.html(), Safelist.none()) }
                ?.replace(Regex("\\s{3,}"), "\n\n")
                ?.trim()
                ?: html.take(maxBodyBytes)

            Pair(title, text)
        } catch (_: Exception) {
            // Fallback: treat as plain text
            Pair(null, html.take(maxBodyBytes))
        }
    }

    private fun mapHttpError(e: RestClientResponseException, url: String): ToolResult.Error {
        val status = e.statusCode.value()
        log.warn("Web fetch HTTP error: url={}, status={}", url, status)
        return when {
            status == 429 -> ToolResult.Error(ToolErrorCode.HTTP_429, "Rate limited by $url")
            status in 500..599 -> ToolResult.Error(ToolErrorCode.HTTP_5XX, "Server error $status from $url")
            else -> ToolResult.Error(ToolErrorCode.UNKNOWN, "HTTP $status from $url")
        }
    }

    private fun mapException(e: Exception, url: String): ToolResult.Error {
        log.warn("Web fetch error: url={}, error={}", url, e.message)
        return when {
            e is SocketTimeoutException || e.cause is SocketTimeoutException ->
                ToolResult.Error(ToolErrorCode.TIMEOUT, "Timeout fetching $url")
            e.message?.contains("connection", ignoreCase = true) == true ->
                ToolResult.Error(ToolErrorCode.NETWORK_ERROR, "Network error fetching $url")
            else -> ToolResult.Error(ToolErrorCode.UNKNOWN, "Failed to fetch $url: ${e.message}")
        }
    }
}
