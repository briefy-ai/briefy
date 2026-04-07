package com.briefy.api.config

import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.ChatMemoryRepository
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChatMemoryConfig {
    @Bean
    fun chatMemory(
        chatMemoryRepository: ChatMemoryRepository,
        @Value("\${chat.conversation.memory.max-messages:20}")
        maxMessages: Int
    ): ChatMemory {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(chatMemoryRepository)
            .maxMessages(maxMessages)
            .build()
    }
}
