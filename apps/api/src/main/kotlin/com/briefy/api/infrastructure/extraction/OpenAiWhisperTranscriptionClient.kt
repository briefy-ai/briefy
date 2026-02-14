package com.briefy.api.infrastructure.extraction

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.io.File

@Component
class OpenAiWhisperTranscriptionClient(
    private val restClientBuilder: RestClient.Builder,
    @param:Value("\${transcription.openai.api-key:\${OPENAI_API_KEY:}}")
    private val apiKey: String,
    @param:Value("\${transcription.openai.model:whisper-1}")
    private val model: String
) {
    private val restClient: RestClient = restClientBuilder.baseUrl("https://api.openai.com").build()

    fun transcribe(audioFile: File): String {
        require(audioFile.exists()) { "Audio file does not exist: ${audioFile.absolutePath}" }
        require(apiKey.isNotBlank()) { "OpenAI transcription API key is not configured" }

        val body = LinkedMultiValueMap<String, Any>().apply {
            add("model", model)
            add("response_format", "json")
            add("file", FileSystemResource(audioFile))
        }

        @Suppress("UNCHECKED_CAST")
        val response = restClient.post()
            .uri("/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(Map::class.java) as? Map<String, Any>

        return response?.get("text")?.toString()?.trim().orEmpty()
    }
}
