package com.briefy.api.config

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "ai.embedding")
class EmbeddingProperties {
    var provider: String = EXPECTED_PROVIDER
    var model: String = EXPECTED_MODEL
    var dimension: Int = EXPECTED_DIMENSION
    var openai = OpenAi()

    class OpenAi {
        var apiKey: String = ""
        var baseUrl: String = "https://api.openai.com"
    }

    @PostConstruct
    fun validateFixedConfig() {
        require(provider == EXPECTED_PROVIDER) {
            "ai.embedding.provider must remain '$EXPECTED_PROVIDER' in V1 (DEC-056)"
        }
        require(model == EXPECTED_MODEL) {
            "ai.embedding.model must remain '$EXPECTED_MODEL' in V1 (DEC-056)"
        }
        require(dimension == EXPECTED_DIMENSION) {
            "ai.embedding.dimension must remain $EXPECTED_DIMENSION in V1 (DEC-056)"
        }
    }

    companion object {
        private const val EXPECTED_PROVIDER = "openai"
        private const val EXPECTED_MODEL = "text-embedding-3-small"
        private const val EXPECTED_DIMENSION = 1536
    }
}
