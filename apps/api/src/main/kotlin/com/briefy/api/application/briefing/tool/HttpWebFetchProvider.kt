package com.briefy.api.application.briefing.tool

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.slf4j.LoggerFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI

class HttpWebFetchProvider(
    private val timeoutMs: Int = 15_000,
    private val maxBodyBytes: Int = 512_000,
    private val ssrfCheckEnabled: Boolean = true
) : WebFetchTool {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun buildRestClient(resolvedAddress: InetAddress? = null): RestClient {
        val factory = object : SimpleClientHttpRequestFactory() {
            override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
                super.prepareConnection(connection, httpMethod)
                connection.instanceFollowRedirects = false
            }
        }.apply {
            setConnectTimeout(timeoutMs)
            setReadTimeout(timeoutMs)
        }

        val builder = RestClient.builder()
            .requestFactory(factory)
            .defaultHeader("User-Agent", "Briefy/1.0 (research assistant)")
            .defaultHeader("Accept", "text/html,application/xhtml+xml,text/plain,application/json")

        if (resolvedAddress != null) {
            builder.defaultHeader("Host", resolvedAddress.hostName)
        }

        return builder.build()
    }

    override fun fetch(url: String): ToolResult<WebFetchResponse> {
        val fetchUri: URI
        val client: RestClient

        if (ssrfCheckEnabled) {
            when (val check = SsrfGuard.validate(url)) {
                is ToolResult.Success -> {
                    val validated = check.data
                    // Pin to resolved IP to prevent DNS rebinding
                    val port = validated.uri.port
                    val scheme = validated.uri.scheme
                    val portSuffix = if (port > 0) ":$port" else ""
                    fetchUri = URI.create("$scheme://${validated.resolvedAddress.hostAddress}$portSuffix${validated.uri.rawPath ?: ""}${validated.uri.rawQuery?.let { "?$it" } ?: ""}")
                    client = buildRestClient(validated.resolvedAddress)
                }
                is ToolResult.Error -> return check
            }
        } else {
            fetchUri = URI.create(url)
            client = buildRestClient()
        }

        return try {
            val body = client.get()
                .uri(fetchUri)
                .retrieve()
                .body(String::class.java)
                ?: return ToolResult.Error(ToolErrorCode.PARSE_ERROR, "Empty response body")

            val bodyBytes = body.toByteArray(Charsets.UTF_8).size
            if (bodyBytes > maxBodyBytes) {
                return ToolResult.Error(
                    ToolErrorCode.CONTENT_TOO_LARGE,
                    "Response body $bodyBytes bytes exceeds limit $maxBodyBytes"
                )
            }

            val extracted = extractReadableContent(body)
            ToolResult.Success(
                WebFetchResponse(
                    url = url,
                    title = extracted.first,
                    content = extracted.second,
                    contentLengthBytes = extracted.second.toByteArray(Charsets.UTF_8).size
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

            doc.select("script, style, nav, footer, header, aside, iframe, noscript, svg, form").remove()

            val mainContent = doc.select("article, main, [role=main]").firstOrNull() ?: doc.body()
            val text = mainContent?.let { Jsoup.clean(it.html(), Safelist.none()) }
                ?.replace(Regex("\\s{3,}"), "\n\n")
                ?.trim()
                ?: html.take(maxBodyBytes)

            Pair(title, text)
        } catch (_: Exception) {
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
