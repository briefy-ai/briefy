package com.briefy.api.infrastructure.ai

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class AiAdapter(
    private val chatClientBuilderProvider: ObjectProvider<ChatClient.Builder>
) {
    private val logger = LoggerFactory.getLogger(AiAdapter::class.java)

    fun complete(prompt: String, systemPrompt: String? = null): String {
        require(prompt.isNotBlank()) { "prompt must not be blank" }

        val chatClientBuilder = chatClientBuilderProvider.ifAvailable
            ?: throw IllegalStateException(
                "AI chat model is not configured. Set LLM_PROVIDER=zhipuai and ZHIPUAI_API_KEY."
            )

        logger.info("[ai] Generating completion promptLength={} hasSystemPrompt={}", prompt.length, !systemPrompt.isNullOrBlank())

        val promptSpec = chatClientBuilder.build().prompt().also {
            if (!systemPrompt.isNullOrBlank()) {
                it.system(systemPrompt)
            }
        }.user(prompt)

        return promptSpec.call().content()?.trim().orEmpty()
    }
}
