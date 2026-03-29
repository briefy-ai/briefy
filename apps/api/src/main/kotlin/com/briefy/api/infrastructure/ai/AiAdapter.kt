package com.briefy.api.infrastructure.ai

import com.google.genai.Client
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.DefaultChatOptions
import org.springframework.ai.google.genai.GoogleGenAiChatModel
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import org.springframework.ai.minimax.MiniMaxChatModel
import org.springframework.ai.minimax.MiniMaxChatOptions
import org.springframework.ai.minimax.api.MiniMaxApi
import org.springframework.ai.zhipuai.ZhiPuAiChatModel
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions
import org.springframework.ai.zhipuai.api.ZhiPuAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class AiAdapter(
    private val restClientBuilder: RestClient.Builder,
    private val aiCallObserver: AiCallObserver,
    @param:Value("\${spring.ai.zhipuai.chat.api-key:}")
    private val zhipuChatApiKey: String,
    @param:Value("\${spring.ai.zhipuai.chat.base-url:https://api.z.ai/api/paas}")
    private val zhipuChatBaseUrl: String,
    @param:Value("\${spring.ai.zhipuai.chat.options.model:glm-4.7-flash}")
    private val zhipuDefaultModel: String,
    @param:Value("\${spring.ai.minimax.chat.api-key:}")
    private val minimaxChatApiKey: String,
    @param:Value("\${spring.ai.minimax.chat.base-url:https://api.minimax.chat}")
    private val minimaxChatBaseUrl: String,
    @param:Value("\${spring.ai.minimax.chat.options.model:MiniMax-M2.5}")
    private val minimaxDefaultModel: String,
    @param:Value("\${spring.ai.google.genai.api-key:}")
    private val googleGenAiApiKey: String,
    @param:Value("\${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}")
    private val googleGenAiDefaultModel: String
) {
    private val logger = LoggerFactory.getLogger(AiAdapter::class.java)
    private val springChatModels: Map<String, ChatModel> by lazy {
        val models = mutableMapOf<String, ChatModel>()
        if (zhipuChatApiKey.isNotBlank()) {
            models["zhipuai"] = ZhiPuAiChatModel(
                ZhiPuAiApi.builder()
                    .apiKey(zhipuChatApiKey)
                    .baseUrl(zhipuChatBaseUrl)
                    .build(),
                ZhiPuAiChatOptions.builder().model(zhipuDefaultModel).build()
            )
        }
        if (minimaxChatApiKey.isNotBlank()) {
            models["minimax"] = MiniMaxChatModel(
                MiniMaxApi(minimaxChatApiKey, minimaxChatBaseUrl, restClientBuilder),
                MiniMaxChatOptions.builder().model(minimaxDefaultModel).build()
            )
        }
        if (googleGenAiApiKey.isNotBlank()) {
            models["google_genai"] = GoogleGenAiChatModel.builder()
                .genAiClient(
                    Client.builder()
                        .apiKey(googleGenAiApiKey)
                        .build()
                )
                .defaultOptions(
                    GoogleGenAiChatOptions.builder()
                        .model(googleGenAiDefaultModel)
                        .build()
                )
                .build()
        }
        models
    }

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
                "zhipuai", "minimax", "google_genai" -> completeWithSpringChatModel(
                    provider = provider,
                    prompt = prompt,
                    systemPrompt = systemPrompt,
                    model = model
                )
                else -> throw IllegalArgumentException("Unsupported AI provider '$provider'")
            }
        }
    }

    private fun completeWithSpringChatModel(
        provider: String,
        prompt: String,
        systemPrompt: String?,
        model: String
    ): String {
        val normalizedProvider = provider.trim().lowercase()
        val chatModel = springChatModels[normalizedProvider]
            ?: throw IllegalStateException(
                "Spring AI chat model is not configured for provider '$provider'. " +
                    "Set the matching provider API key."
            )

        val promptSpec = buildPromptSpec(
            requestSpec = ChatClient.builder(chatModel).build().prompt(),
            prompt = prompt,
            systemPrompt = systemPrompt,
            model = model
        )

        return if (normalizedProvider == "google_genai") {
            try {
                extractSpringResponse(normalizedProvider, promptSpec.call())
            } catch (error: RuntimeException) {
                throw GoogleGenAiSpringSupport.unwrapFailure(error)
            }
        } else {
            extractSpringResponse(normalizedProvider, promptSpec.call())
        }
    }

    internal fun buildPromptSpec(
        requestSpec: ChatClient.ChatClientRequestSpec,
        prompt: String,
        systemPrompt: String?,
        model: String
    ): ChatClient.ChatClientRequestSpec {
        if (!systemPrompt.isNullOrBlank()) {
            requestSpec.system(systemPrompt)
        }

        val chatOptions = DefaultChatOptions()
        chatOptions.setModel(model)
        requestSpec.options(chatOptions)

        return requestSpec.user(prompt)
    }

    internal fun extractSpringResponse(provider: String, responseSpec: ChatClient.CallResponseSpec): String {
        return if (provider.trim().lowercase() == "google_genai") {
            GoogleGenAiSpringSupport.extractText(responseSpec.chatResponse() ?: ChatResponse(emptyList()))
        } else {
            responseSpec.content()?.trim().orEmpty()
        }
    }
}
