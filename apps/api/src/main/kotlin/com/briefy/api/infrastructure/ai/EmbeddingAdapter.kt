package com.briefy.api.infrastructure.ai

import com.briefy.api.config.EmbeddingProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class EmbeddingAdapter(
    private val restClientBuilder: RestClient.Builder,
    private val embeddingProperties: EmbeddingProperties
) {
    fun embed(text: String): List<Double> {
        require(text.isNotBlank()) { "text must not be blank" }

        return when (embeddingProperties.provider.trim().lowercase()) {
            PROVIDER_OPENAI -> embedWithOpenAi(text)
            else -> throw IllegalArgumentException("Unsupported embedding provider '${embeddingProperties.provider}'")
        }
    }

    private fun embedWithOpenAi(text: String): List<Double> {
        val apiKey = embeddingProperties.openai.apiKey.trim()
        require(apiKey.isNotBlank()) { "OpenAI embedding API key is not configured" }

        val model = embeddingProperties.model.trim()
        require(model.isNotBlank()) { "Embedding model is not configured" }

        val baseUrl = embeddingProperties.openai.baseUrl.trim().removeSuffix("/")
        val response = restClientBuilder
            .baseUrl(baseUrl)
            .build()
            .post()
            .uri("/v1/embeddings")
            .header("Authorization", "Bearer $apiKey")
            .body(
                mapOf(
                    "model" to model,
                    "input" to text
                )
            )
            .retrieve()
            .body(OpenAiEmbeddingResponse::class.java)
            ?: throw IllegalStateException("OpenAI embedding response is empty")

        val embedding = response.data.firstOrNull()?.embedding
            ?: throw IllegalStateException("OpenAI embedding response has no embedding data")

        if (embedding.size != embeddingProperties.dimension) {
            throw IllegalStateException(
                "Unexpected embedding dimension ${embedding.size}; expected ${embeddingProperties.dimension}"
            )
        }

        return embedding
    }

    private data class OpenAiEmbeddingResponse(
        val data: List<OpenAiEmbeddingData> = emptyList()
    )

    private data class OpenAiEmbeddingData(
        val embedding: List<Double> = emptyList()
    )

    companion object {
        private const val PROVIDER_OPENAI = "openai"
    }
}
