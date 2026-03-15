package com.briefy.api.infrastructure.tts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import java.util.Base64

@Component
class InworldTtsAdapter(
    restClientBuilder: RestClient.Builder,
    private val properties: InworldTtsProperties,
    private val objectMapper: ObjectMapper
) : TtsProvider {
    override val type = TtsProviderType.INWORLD

    private val restClient = restClientBuilder
        .baseUrl(properties.baseUrl)
        .build()

    override fun synthesize(request: TtsSynthesisRequest): ByteArray {
        require(request.text.isNotBlank()) { "Narration text must not be blank" }

        try {
            val response = restClient.post()
                .uri("/tts/v1/voice")
                .header("Authorization", "Basic ${request.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "modelId" to request.modelId,
                        "text" to request.text,
                        "voiceId" to request.voiceId
                    )
                )
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("Inworld returned an empty response")

            return parseAudioBytes(response)
        } catch (ex: HttpClientErrorException) {
            throw mapClientError(ex)
        } catch (ex: HttpServerErrorException) {
            throw InworldTtsException(
                code = "inworld_server_error",
                userMessage = "Inworld is temporarily unavailable. Try again in a moment.",
                retryable = true,
                cause = ex
            )
        }
    }

    private fun parseAudioBytes(responseBody: String): ByteArray {
        val audioContent = objectMapper.readTree(responseBody).path("audioContent").asText("")
        if (audioContent.isBlank()) {
            throw IllegalStateException("Inworld returned an empty audio payload")
        }
        return Base64.getDecoder().decode(audioContent)
    }

    private fun mapClientError(ex: HttpClientErrorException): InworldTtsException {
        val detail = parseErrorDetail(ex.responseBodyAsString)
        val message = detail["message"] ?: "Inworld could not generate audio for this source."

        return when {
            ex.statusCode.value() == 401 || ex.statusCode.value() == 403 -> InworldTtsException(
                code = "inworld_invalid_api_key",
                userMessage = "Your Inworld API key is invalid. Update it in Settings and try again.",
                retryable = false,
                cause = ex
            )
            ex.statusCode.value() == 429 -> InworldTtsException(
                code = "inworld_rate_limited",
                userMessage = message,
                retryable = true,
                cause = ex
            )
            ex.statusCode.value() in listOf(408, 409, 425) -> InworldTtsException(
                code = "inworld_request_retryable",
                userMessage = message,
                retryable = true,
                cause = ex
            )
            else -> InworldTtsException(
                code = detail["code"] ?: "inworld_request_failed",
                userMessage = message,
                retryable = false,
                cause = ex
            )
        }
    }

    private fun parseErrorDetail(responseBody: String?): Map<String, String> {
        if (responseBody.isNullOrBlank()) return emptyMap()

        return try {
            val root = objectMapper.readTree(responseBody)
            val errorNode = root.path("error")
            when {
                !errorNode.isMissingNode && !errorNode.isNull -> parseStructuredError(errorNode)
                else -> mapOf("message" to root.path("message").asText("").ifBlank { "" })
            }.filterValues { it.isNotBlank() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseStructuredError(errorNode: JsonNode): Map<String, String> {
        return mapOf(
            "code" to errorNode.path("code").asText(""),
            "message" to errorNode.path("message").asText("")
        )
    }
}
