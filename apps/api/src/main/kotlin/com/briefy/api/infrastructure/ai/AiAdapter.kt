package com.briefy.api.infrastructure.ai

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.DefaultChatOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class AiAdapter(
    private val chatModelProvider: ObjectProvider<ChatModel>,
    private val restClientBuilder: RestClient.Builder,
    private val aiCallObserver: AiCallObserver,
    @param:Value("\${spring.ai.google.genai.api-key:}")
    private val googleGenAiApiKey: String
) {
    private val logger = LoggerFactory.getLogger(AiAdapter::class.java)

    fun complete(
        provider: String,
        model: String,
        prompt: String,
        systemPrompt: String? = null,
        useCase: String? = null
    ): String {
        require(prompt.isNotBlank()) { "prompt must not be blank" }
        require(provider.isNotBlank()) { "provider must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }

        logger.info(
            "[ai] Generating completion provider={} model={} promptLength={} hasSystemPrompt={}",
            provider,
            model,
            prompt.length,
            !systemPrompt.isNullOrBlank()
        )

        return aiCallObserver.observeCompletion(
            provider = provider,
            model = model,
            useCase = useCase,
            prompt = prompt,
            systemPrompt = systemPrompt
        ) {
            when (provider.trim().lowercase()) {
                "zhipuai" -> completeWithSpringChatModel(prompt = prompt, systemPrompt = systemPrompt, model = model)
                "google_genai" -> completeWithGoogleGenAi(prompt = prompt, systemPrompt = systemPrompt, model = model)
                else -> throw IllegalArgumentException("Unsupported AI provider '$provider'")
            }
        }
    }

    private fun completeWithSpringChatModel(prompt: String, systemPrompt: String?, model: String): String {
        val chatModel = chatModelProvider.ifAvailable
            ?: throw IllegalStateException(
                "ZhipuAI chat model is not configured. Set LLM_PROVIDER=zhipuai and ZHIPUAI_API_KEY."
            )

        val promptSpec = ChatClient.builder(chatModel).build().prompt().also {
            if (!systemPrompt.isNullOrBlank()) {
                it.system(systemPrompt)
            }
            val chatOptions = DefaultChatOptions()
            chatOptions.setModel(model)
            it.options(chatOptions)
        }.user(prompt)

        return promptSpec.call().content()?.trim().orEmpty()
    }

    private fun completeWithGoogleGenAi(prompt: String, systemPrompt: String?, model: String): String {
        require(googleGenAiApiKey.isNotBlank()) { "Google GenAI is not configured on this server" }

        val requestBody = mutableMapOf<String, Any>(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to prompt))
                )
            )
        )
        if (!systemPrompt.isNullOrBlank()) {
            requestBody["systemInstruction"] = mapOf(
                "parts" to listOf(mapOf("text" to systemPrompt))
            )
        }

        val encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8)
        val encodedApiKey = URLEncoder.encode(googleGenAiApiKey, StandardCharsets.UTF_8)
        val uri = "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent?key=$encodedApiKey"

        val responseJson = restClientBuilder.build()
            .post()
            .uri(uri)
            .body(requestBody)
            .retrieve()
            .body(Map::class.java) ?: emptyMap<String, Any>()

        @Suppress("UNCHECKED_CAST")
        val candidates = responseJson["candidates"] as? List<Map<String, Any>> ?: emptyList()
        if (candidates.isEmpty()) {
            return ""
        }

        @Suppress("UNCHECKED_CAST")
        val firstCandidate = candidates.firstOrNull() ?: return ""
        @Suppress("UNCHECKED_CAST")
        val content = firstCandidate["content"] as? Map<String, Any> ?: return ""
        @Suppress("UNCHECKED_CAST")
        val parts = content["parts"] as? List<Map<String, Any>> ?: return ""
        return parts.joinToString("\n") { (it["text"] as? String).orEmpty() }.trim()
    }
}
