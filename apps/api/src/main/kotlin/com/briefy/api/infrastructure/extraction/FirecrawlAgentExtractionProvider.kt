package com.briefy.api.infrastructure.extraction

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

class FirecrawlAgentExtractionProvider(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val apiKey: String,
    private val model: String,
    private val pollIntervalMs: Long,
    private val maxWaitMs: Long,
    private val maxCredits: Int?
) : ExtractionProvider {
    override val id: ExtractionProviderId = ExtractionProviderId.FIRECRAWL

    override fun extract(url: String): ExtractionResult {
        val jobId = startAgentJob(url)
        val terminalState = waitForCompletion(jobId)
        val extractedText = readExtractedText(terminalState).trim()
        if (extractedText.isBlank()) {
            throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.UNKNOWN,
                message = "Firecrawl agent returned empty content"
            )
        }
        return ExtractionResult(
            text = extractedText,
            title = null,
            author = null,
            publishedDate = null,
            aiFormatted = false
        )
    }

    private fun startAgentJob(url: String): String {
        val payload = linkedMapOf<String, Any>(
            "prompt" to POSTHOG_PROMPT,
            "schema" to POSTHOG_SCHEMA,
            "urls" to listOf(url),
            "model" to model,
            "strictConstrainToUrls" to true
        )
        if (maxCredits != null) {
            payload["maxCredits"] = maxCredits
        }

        val response = postJson("/v2/agent", payload)
        return firstText(response, "id", "jobId", "agentId")
            ?: firstText(response.path("data"), "id", "jobId", "agentId")
            ?: throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.UNKNOWN,
                message = "Firecrawl agent did not return a job id"
            )
    }

    private fun waitForCompletion(jobId: String): JsonNode {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() <= deadline) {
            val statusResponse = getJson("/v2/agent/$jobId")
            val status = firstText(statusResponse, "status")
                ?: firstText(statusResponse.path("data"), "status")
                ?: "unknown"

            when (status.lowercase()) {
                "completed" -> return statusResponse
                "failed", "error", "cancelled" -> {
                    val failureMessage = firstText(statusResponse, "error", "message")
                        ?: firstText(statusResponse.path("data"), "error", "message")
                        ?: "Firecrawl agent job failed"
                    throw ExtractionProviderException(
                        providerId = id,
                        reason = ExtractionFailureReason.UNKNOWN,
                        message = failureMessage
                    )
                }
            }

            try {
                Thread.sleep(pollIntervalMs.coerceAtLeast(1))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ExtractionProviderException(
                    providerId = id,
                    reason = ExtractionFailureReason.TIMEOUT,
                    message = "Firecrawl agent polling interrupted"
                )
            }
        }

        throw ExtractionProviderException(
            providerId = id,
            reason = ExtractionFailureReason.TIMEOUT,
            message = "Firecrawl agent timed out while extracting content"
        )
    }

    private fun readExtractedText(node: JsonNode): String {
        val data = node.path("data")
        val output = data.path("output")
        val extract = data.path("extract")
        val finalOutput = data.path("finalOutput")

        return listOf(
            node.path(POSTHOG_TEXT_FIELD).asText(""),
            data.path(POSTHOG_TEXT_FIELD).asText(""),
            output.path(POSTHOG_TEXT_FIELD).asText(""),
            extract.path(POSTHOG_TEXT_FIELD).asText(""),
            finalOutput.path(POSTHOG_TEXT_FIELD).asText("")
        ).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun postJson(uri: String, body: Any): JsonNode {
        return executeJsonRequest {
            restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $apiKey")
                .body(body)
        }
    }

    private fun getJson(uri: String): JsonNode {
        return executeJsonRequest {
            restClient.get()
                .uri(uri)
                .header("Authorization", "Bearer $apiKey")
        }
    }

    private fun executeJsonRequest(requestBuilder: () -> RestClient.RequestHeadersSpec<*>): JsonNode {
        val rawResponse = try {
            requestBuilder()
                .retrieve()
                .onStatus(HttpStatusCode::isError) { _, clientResponse ->
                    throw ExtractionProviderException(
                        providerId = id,
                        reason = ExtractionFailureReason.NETWORK,
                        message = "Firecrawl returned status ${clientResponse.statusCode}"
                    )
                }
                .body(String::class.java)
        } catch (e: ExtractionProviderException) {
            throw e
        } catch (e: RestClientException) {
            throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.NETWORK,
                message = "Firecrawl request failed",
                cause = e
            )
        }

        return try {
            objectMapper.readTree(rawResponse ?: "{}")
        } catch (e: Exception) {
            throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.UNKNOWN,
                message = "Firecrawl returned an invalid payload",
                cause = e
            )
        }
    }

    private fun firstText(node: JsonNode, vararg fields: String): String? {
        return fields.asSequence()
            .map { field -> node.path(field).asText("").trim() }
            .firstOrNull { it.isNotBlank() }
    }

    companion object {
        private const val POSTHOG_TEXT_FIELD = "posthog_article_text"
        private const val POSTHOG_PROMPT = "Extract the full article text from this PostHog URL."
        private val POSTHOG_SCHEMA = mapOf(
            "type" to "object",
            "properties" to mapOf(
                POSTHOG_TEXT_FIELD to mapOf(
                    "type" to "string",
                    "description" to "The full text content of the PostHog article."
                ),
                "posthog_article_text_citation" to mapOf(
                    "type" to "string",
                    "description" to "Source URL for posthog_article_text"
                )
            ),
            "required" to listOf(POSTHOG_TEXT_FIELD)
        )
    }
}
