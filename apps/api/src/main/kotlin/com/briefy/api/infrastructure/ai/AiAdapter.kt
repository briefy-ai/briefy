package com.briefy.api.infrastructure.ai

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.stereotype.Component

@Component
class AiAdapter(
    private val chatModelProvider: ObjectProvider<ChatModel>
) {
    private val logger = LoggerFactory.getLogger(AiAdapter::class.java)

    fun complete(prompt: String, systemPrompt: String? = null): String {
        require(prompt.isNotBlank()) { "prompt must not be blank" }

        val chatModel = chatModelProvider.ifAvailable
            ?: throw IllegalStateException(
                "AI chat model is not configured. Set LLM_PROVIDER=zhipuai and ZHIPUAI_API_KEY. " +
                    "If using a Z.ai key, set ZHIPUAI_BASE_URL=https://api.z.ai/api/paas."
            )

        logger.info("[ai] Generating completion promptLength={} hasSystemPrompt={}", prompt.length, !systemPrompt.isNullOrBlank())

        val promptSpec = ChatClient.builder(chatModel).build().prompt().also {
            if (!systemPrompt.isNullOrBlank()) {
                it.system(systemPrompt)
            }
        }.user(prompt)

        return promptSpec.call().content()?.trim().orEmpty()
    }
}
