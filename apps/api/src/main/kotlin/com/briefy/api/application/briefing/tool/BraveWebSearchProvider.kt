package com.briefy.api.application.briefing.tool

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.SocketTimeoutException

class BraveWebSearchProvider(
    private val apiKey: String,
    private val objectMapper: ObjectMapper,
    baseUrl: String = "https://api.search.brave.com",
    timeoutMs: Int = 10_000
) : WebSearchTool {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(timeoutMs)
            setReadTimeout(timeoutMs)
        })
        .defaultHeader("Accept", "application/json")
        .defaultHeader("X-Subscription-Token", apiKey)
        .build()

    override fun search(query: String, maxResults: Int): ToolResult<WebSearchResponse> {
        return try {
            val body = restClient.get()
                .uri("/res/v1/web/search?q={q}&count={count}", query, maxResults.coerceIn(1, 20))
                .retrieve()
                .body(String::class.java)
                ?: return ToolResult.Error(ToolErrorCode.PARSE_ERROR, "Empty response from Brave")

            val root = objectMapper.readTree(body)
            val webResults = root.path("web").path("results")
            val results = parseResults(webResults)

            ToolResult.Success(WebSearchResponse(results = results, query = query))
        } catch (e: RestClientResponseException) {
            mapHttpError(e)
        } catch (e: Exception) {
            mapException(e)
        }
    }

    private fun parseResults(resultsNode: JsonNode): List<WebSearchResult> {
        if (!resultsNode.isArray) return emptyList()
        return resultsNode.mapNotNull { node ->
            val title = node.path("title").asText("")
            val url = node.path("url").asText("")
            val snippet = node.path("description").asText("")
            if (url.isBlank()) null
            else WebSearchResult(title = title, url = url, snippet = snippet)
        }
    }

    private fun mapHttpError(e: RestClientResponseException): ToolResult.Error {
        val status = e.statusCode.value()
        log.warn("Brave search HTTP error: status={}, body={}", status, e.responseBodyAsString.take(200))
        return when {
            status == 401 || status == 403 -> ToolResult.Error(ToolErrorCode.PROVIDER_AUTH_ERROR, "Brave API auth failed: $status")
            status == 429 -> ToolResult.Error(ToolErrorCode.HTTP_429, "Brave rate limited")
            status in 500..599 -> ToolResult.Error(ToolErrorCode.HTTP_5XX, "Brave server error: $status")
            else -> ToolResult.Error(ToolErrorCode.UNKNOWN, "Brave HTTP $status")
        }
    }

    private fun mapException(e: Exception): ToolResult.Error {
        log.warn("Brave search error: {}", e.message)
        return when {
            e is SocketTimeoutException || e.cause is SocketTimeoutException ->
                ToolResult.Error(ToolErrorCode.TIMEOUT, "Brave search timeout")
            e.message?.contains("connection", ignoreCase = true) == true ->
                ToolResult.Error(ToolErrorCode.NETWORK_ERROR, "Brave network error: ${e.message}")
            else -> ToolResult.Error(ToolErrorCode.UNKNOWN, "Brave search failed: ${e.message}")
        }
    }
}
