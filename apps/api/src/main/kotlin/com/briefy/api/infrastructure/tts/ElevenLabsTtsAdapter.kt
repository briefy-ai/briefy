package com.briefy.api.infrastructure.tts

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

@Component
class ElevenLabsTtsAdapter(
    restClientBuilder: RestClient.Builder,
    private val properties: TtsProperties,
    private val objectMapper: ObjectMapper
) {
    private val restClient = restClientBuilder
        .baseUrl(properties.baseUrl)
        .build()

    fun synthesize(text: String, apiKey: String): ByteArray {
        require(text.isNotBlank()) { "Narration text must not be blank" }

        try {
            return restClient.post()
                .uri { builder ->
                    builder
                        .path("/v1/text-to-speech/{voiceId}")
                        .queryParam("output_format", OUTPUT_FORMAT)
                        .build(properties.voiceId)
                }
                .header("xi-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .body(
                    mapOf(
                        "text" to text,
                        "model_id" to properties.modelId
                    )
                )
                .retrieve()
                .body(ByteArray::class.java)
                ?: throw IllegalStateException("ElevenLabs returned an empty audio payload")
        } catch (ex: HttpClientErrorException) {
            throw mapClientError(ex)
        } catch (ex: HttpServerErrorException) {
            throw ElevenLabsTtsException(
                code = "elevenlabs_server_error",
                userMessage = "ElevenLabs is temporarily unavailable. Try again in a moment.",
                retryable = true,
                cause = ex
            )
        }
    }

    private fun mapClientError(ex: HttpClientErrorException): ElevenLabsTtsException {
        val detail = parseErrorDetail(ex.responseBodyAsString)
        val providerCode = detail["code"]?.ifBlank { null }

        return when (providerCode) {
            "paid_plan_required" -> ElevenLabsTtsException(
                code = providerCode,
                userMessage = "Your ElevenLabs API key cannot use the configured voice. Free ElevenLabs plans cannot use library voices via API.",
                retryable = false,
                cause = ex
            )
            "invalid_api_key" -> ElevenLabsTtsException(
                code = providerCode,
                userMessage = "Your ElevenLabs API key is invalid. Update it in Settings and try again.",
                retryable = false,
                cause = ex
            )
            "quota_exceeded", "too_many_concurrent_requests", "system_busy", "voice_not_ready" -> ElevenLabsTtsException(
                code = providerCode,
                userMessage = detail["message"] ?: "ElevenLabs is temporarily unable to generate audio. Try again shortly.",
                retryable = true,
                cause = ex
            )
            else -> mapUnknownClientError(ex, detail)
        }
    }

    private fun mapUnknownClientError(
        ex: HttpClientErrorException,
        detail: Map<String, String>
    ): ElevenLabsTtsException {
        val retryable = ex.statusCode.value() in listOf(408, 409, 425, 429)
        return ElevenLabsTtsException(
            code = if (retryable) "elevenlabs_request_retryable" else "elevenlabs_request_failed",
            userMessage = detail["message"] ?: "ElevenLabs could not generate audio for this source.",
            retryable = retryable,
            cause = ex
        )
    }

    private fun parseErrorDetail(responseBody: String?): Map<String, String> {
        if (responseBody.isNullOrBlank()) return emptyMap()

        return try {
            val detailNode = objectMapper.readTree(responseBody).path("detail")
            if (detailNode.isMissingNode || detailNode.isNull) {
                emptyMap()
            } else {
                mapOf(
                    "type" to detailNode.path("type").asText(""),
                    "code" to detailNode.path("code").asText(""),
                    "message" to detailNode.path("message").asText("")
                )
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val OUTPUT_FORMAT = "mp3_44100_128"
    }
}
