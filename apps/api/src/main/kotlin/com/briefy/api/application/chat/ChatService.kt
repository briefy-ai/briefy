package com.briefy.api.application.chat

import com.briefy.api.domain.chat.ChatEntityType
import com.briefy.api.domain.chat.ChatMessage
import com.briefy.api.domain.chat.ChatMessageRepository
import com.briefy.api.domain.chat.ChatMessageRole
import com.briefy.api.domain.chat.ChatMessageType
import com.briefy.api.domain.chat.Conversation
import com.briefy.api.domain.chat.ConversationRepository
import com.briefy.api.domain.knowledgegraph.briefing.Briefing
import com.briefy.api.domain.knowledgegraph.briefing.BriefingRepository
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

@Service
class ChatService(
    private val conversationRepository: ConversationRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val sourceRepository: SourceRepository,
    private val briefingRepository: BriefingRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val idGenerator: IdGenerator,
    private val aiAdapter: AiAdapter,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate,
    @param:Value("\${chat.conversation.provider:google_genai}")
    private val chatProvider: String,
    @param:Value("\${chat.conversation.model:gemini-2.5-flash}")
    private val chatModel: String
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val defaultListLimit = 20
    private val maxListLimit = 100
    private val maxPromptHistoryMessages = 20

    fun sendMessage(command: SendChatMessageCommand): Flux<ServerSentEvent<String>> {
        val userId = currentUserProvider.requireUserId()
        val preparedTurn = prepareTurn(userId, command)
        val assistantContent = StringBuilder()

        return aiAdapter.stream(
            provider = chatProvider,
            model = chatModel,
            prompt = preparedTurn.prompt,
            systemPrompt = preparedTurn.systemPrompt,
            useCase = "chat_conversation"
        )
            .filter { it.isNotEmpty() }
            .map { token ->
                assistantContent.append(token)
                toSse(preparedTurn.conversation.id, ChatTokenStreamEvent(
                    conversationId = preparedTurn.conversation.id,
                    content = token
                ))
            }
            .concatWith(Mono.defer {
                finalizeAssistantMessage(preparedTurn, assistantContent.toString())
            })
            .onErrorResume { error ->
                logger.error(
                    "[chat] Streaming assistant response failed conversationId={} userId={}",
                    preparedTurn.conversation.id,
                    userId,
                    error
                )
                Mono.just(
                    toSse(
                        preparedTurn.conversation.id,
                        ChatErrorStreamEvent(
                            conversationId = preparedTurn.conversation.id,
                            message = "Failed to generate assistant response"
                        )
                    )
                )
            }
    }

    fun listConversations(limit: Int?, cursor: String?): ChatConversationPageResponse {
        val userId = currentUserProvider.requireUserId()
        val normalizedLimit = (limit ?: defaultListLimit).coerceIn(1, maxListLimit)
        val decodedCursor = cursor?.let { ConversationListCursorCodec.decode(it) }

        val conversations = transactionTemplate.execute {
            conversationRepository.findConversationPage(
                userId = userId,
                cursorUpdatedAt = decodedCursor?.updatedAt,
                cursorId = decodedCursor?.id,
                limit = normalizedLimit
            )
        }.orEmpty()

        val hasMore = conversations.size > normalizedLimit
        val pageItems = if (hasMore) conversations.dropLast(1) else conversations
        val latestPreviews = if (pageItems.isEmpty()) {
            emptyMap()
        } else {
            transactionTemplate.execute {
                chatMessageRepository.findLatestContentByConversationIds(pageItems.map { it.id })
                    .associate { preview ->
                        preview.conversationId to preview.content
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?.take(200)
                    }
            }.orEmpty()
        }
        val nextCursor = if (hasMore && pageItems.isNotEmpty()) {
            val lastItem = pageItems.last()
            ConversationListCursorCodec.encode(
                ConversationListCursor(
                    updatedAt = lastItem.updatedAt,
                    id = lastItem.id
                )
            )
        } else {
            null
        }

        val items = pageItems.map { conversation ->
            ChatConversationSummaryResponse(
                id = conversation.id,
                title = conversation.title,
                updatedAt = conversation.updatedAt,
                lastMessagePreview = latestPreviews[conversation.id]
            )
        }

        return ChatConversationPageResponse(
            items = items,
            nextCursor = nextCursor,
            hasMore = hasMore,
            limit = normalizedLimit
        )
    }

    fun getConversation(conversationId: UUID): ChatConversationResponse {
        val userId = currentUserProvider.requireUserId()
        val conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
            ?: throw ChatConversationNotFoundException(conversationId)
        val messages = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.id)

        return ChatConversationResponse(
            id = conversation.id,
            title = conversation.title,
            messages = messages.map(::toMessageResponse),
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt
        )
    }

    fun deleteConversation(conversationId: UUID) {
        val userId = currentUserProvider.requireUserId()
        val conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
            ?: throw ChatConversationNotFoundException(conversationId)

        transactionTemplate.executeWithoutResult {
            chatMessageRepository.deleteByConversationId(conversation.id)
            conversationRepository.deleteById(conversation.id)
        }
    }

    private fun prepareTurn(userId: UUID, command: SendChatMessageCommand): PreparedChatTurn {
        val trimmedText = command.text.trim()
        if (trimmedText.isBlank()) {
            throw InvalidChatRequestException("text must not be blank")
        }

        val dedupedReferences = command.contentReferences
            .distinctBy { "${it.type.lowercase()}:${it.id}" }

        return transactionTemplate.execute {
            val conversation = resolveConversation(userId, command.conversationIdOrNew)
            val history = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.id)
            val resolvedReferences = resolveReferences(userId, dedupedReferences)
            val now = Instant.now()

            val userMessage = ChatMessage(
                id = idGenerator.newId(),
                conversationId = conversation.id,
                role = ChatMessageRole.USER,
                type = ChatMessageType.USER_TEXT,
                content = trimmedText,
                payload = buildUserPayload(resolvedReferences),
                entityType = primaryEntityType(resolvedReferences),
                entityId = primaryEntityId(resolvedReferences),
                createdAt = now
            )

            conversation.touch(now)
            conversationRepository.save(conversation)
            chatMessageRepository.save(userMessage)

            PreparedChatTurn(
                conversation = conversation,
                history = history,
                userMessage = userMessage,
                systemPrompt = buildSystemPrompt(resolvedReferences),
                prompt = buildPrompt(history, trimmedText)
            )
        } ?: error("Failed to prepare chat turn")
    }

    private fun resolveConversation(userId: UUID, conversationIdOrNew: String): Conversation {
        if (conversationIdOrNew == NEW_CONVERSATION_ID) {
            val now = Instant.now()
            val conversation = Conversation(
                id = idGenerator.newId(),
                userId = userId,
                createdAt = now,
                updatedAt = now
            )
            return conversationRepository.save(conversation)
        }

        val conversationId = parseConversationId(conversationIdOrNew)
        return conversationRepository.findByIdAndUserId(conversationId, userId)
            ?: throw ChatConversationNotFoundException(conversationId)
    }

    private fun resolveReferences(
        userId: UUID,
        references: List<ChatContentReferenceInput>
    ): List<ResolvedReference> {
        return references.map { reference ->
            when (reference.type.trim().lowercase()) {
                "source" -> resolveSourceReference(userId, reference.id)
                "briefing" -> resolveBriefingReference(userId, reference.id)
                else -> throw InvalidChatRequestException("Unsupported content reference type '${reference.type}'")
            }
        }
    }

    private fun resolveSourceReference(userId: UUID, id: UUID): ResolvedReference {
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: throw ChatReferenceAccessException("source", id)
        if (source.status != SourceStatus.ACTIVE) {
            throw InvalidChatRequestException("Referenced source '$id' must be active")
        }
        val content = source.content?.text?.trim().orEmpty()
        if (content.isBlank()) {
            throw InvalidChatRequestException("Referenced source '$id' has no extracted content")
        }

        return ResolvedReference(
            id = source.id,
            type = ChatEntityType.SOURCE,
            label = source.metadata?.title?.trim().takeUnless { it.isNullOrBlank() } ?: source.url.normalized,
            content = content
        )
    }

    private fun resolveBriefingReference(userId: UUID, id: UUID): ResolvedReference {
        val briefing = briefingRepository.findByIdAndUserId(id, userId)
            ?: throw ChatReferenceAccessException("briefing", id)
        val content = briefing.contentMarkdown?.trim().orEmpty()
        if (content.isBlank()) {
            throw InvalidChatRequestException("Referenced briefing '$id' has no content")
        }

        return ResolvedReference(
            id = briefing.id,
            type = ChatEntityType.BRIEFING,
            label = briefing.title?.trim().takeUnless { it.isNullOrBlank() } ?: "Briefing ${briefing.id}",
            content = content
        )
    }

    private fun buildSystemPrompt(references: List<ResolvedReference>): String {
        val instructions = buildString {
            appendLine("You are Briefy, a knowledge assistant for a personal research and reading platform.")
            appendLine("Answer clearly and directly.")
            appendLine("If referenced content is provided, prioritize it and say when the answer depends on that context.")
            appendLine("If no referenced content is provided, answer from general knowledge.")
        }

        if (references.isEmpty()) {
            return instructions.trim()
        }

        val contextBlocks = references.joinToString("\n\n") { reference ->
            buildString {
                appendLine("Referenced ${reference.type.name.lowercase()}: ${reference.label}")
                appendLine("Reference ID: ${reference.id}")
                appendLine(reference.content)
            }.trim()
        }

        return buildString {
            appendLine(instructions.trim())
            appendLine()
            appendLine("Referenced content:")
            appendLine(contextBlocks)
        }.trim()
    }

    private fun buildPrompt(history: List<ChatMessage>, text: String): String {
        val recentHistory = history.takeLast(maxPromptHistoryMessages)
        if (recentHistory.isEmpty()) {
            return text
        }

        val historyBlock = recentHistory.joinToString("\n\n") { message ->
            val speaker = when (message.role) {
                ChatMessageRole.USER -> "User"
                ChatMessageRole.ASSISTANT -> "Assistant"
                ChatMessageRole.SYSTEM -> "System"
            }
            val content = message.content?.trim().orEmpty()
            "$speaker: $content"
        }

        return buildString {
            appendLine("Conversation history:")
            appendLine(historyBlock)
            appendLine()
            appendLine("Latest user message:")
            append(text)
        }
    }

    private fun buildUserPayload(references: List<ResolvedReference>): JsonNode? {
        if (references.isEmpty()) {
            return null
        }

        val payload = objectMapper.createObjectNode()
        val refsNode = payload.putArray("contentReferences")
        references.forEach { reference ->
            refsNode.addObject()
                .put("id", reference.id.toString())
                .put("type", reference.type.name.lowercase())
        }
        return payload
    }

    private fun primaryEntityType(references: List<ResolvedReference>): ChatEntityType? {
        return references.singleOrNull()?.type
    }

    private fun primaryEntityId(references: List<ResolvedReference>): UUID? {
        return references.singleOrNull()?.id
    }

    private fun finalizeAssistantMessage(
        preparedTurn: PreparedChatTurn,
        assistantText: String
    ): Mono<ServerSentEvent<String>> {
        val trimmedText = assistantText.trim()
        if (trimmedText.isBlank()) {
            return Mono.just(
                toSse(
                    preparedTurn.conversation.id,
                    ChatErrorStreamEvent(
                        conversationId = preparedTurn.conversation.id,
                        message = "Assistant returned an empty response"
                    )
                )
            )
        }

        return Mono.fromCallable {
            val message = transactionTemplate.execute {
                val now = Instant.now()
                val assistantMessage = ChatMessage(
                    id = idGenerator.newId(),
                    conversationId = preparedTurn.conversation.id,
                    role = ChatMessageRole.ASSISTANT,
                    type = ChatMessageType.ASSISTANT_TEXT,
                    content = trimmedText,
                    createdAt = now
                )

                val conversation = conversationRepository.findByIdAndUserId(preparedTurn.conversation.id, preparedTurn.conversation.userId)
                    ?: throw ChatConversationAccessException()
                conversation.touch(now)
                conversationRepository.save(conversation)
                chatMessageRepository.save(assistantMessage)
            } ?: error("Failed to persist assistant message")

            toSse(
                preparedTurn.conversation.id,
                ChatMessageStreamEvent(
                    conversationId = preparedTurn.conversation.id,
                    message = toMessageResponse(message)
                )
            )
        }
            .subscribeOn(Schedulers.boundedElastic())
    }

    private fun parseConversationId(rawValue: String): UUID {
        return try {
            UUID.fromString(rawValue)
        } catch (_: IllegalArgumentException) {
            throw InvalidChatRequestException("Invalid conversation id '$rawValue'")
        }
    }

    private fun toMessageResponse(message: ChatMessage): ChatMessageResponse {
        return ChatMessageResponse(
            id = message.id,
            role = message.role.name.lowercase(),
            type = message.type.name.lowercase(),
            content = message.content,
            contentReferences = extractContentReferences(message.payload),
            entityType = message.entityType?.name?.lowercase(),
            entityId = message.entityId,
            createdAt = message.createdAt
        )
    }

    private fun extractContentReferences(payload: JsonNode?): List<ChatContentReferenceResponse> {
        val refsNode = payload?.path("contentReferences")
        if (refsNode == null || !refsNode.isArray) {
            return emptyList()
        }

        return refsNode.mapNotNull { ref ->
            val idText = ref.path("id").asText("").trim()
            val typeText = ref.path("type").asText("").trim()
            if (idText.isBlank() || typeText.isBlank()) {
                return@mapNotNull null
            }

            val id = runCatching { UUID.fromString(idText) }.getOrNull() ?: return@mapNotNull null
            ChatContentReferenceResponse(
                id = id,
                type = typeText
            )
        }
    }

    private fun toSse(conversationId: UUID, payload: ChatStreamEventPayload): ServerSentEvent<String> {
        return ServerSentEvent.builder(objectMapper.writeValueAsString(payload))
            .id(conversationId.toString())
            .build()
    }

    private data class PreparedChatTurn(
        val conversation: Conversation,
        val history: List<ChatMessage>,
        val userMessage: ChatMessage,
        val systemPrompt: String,
        val prompt: String
    )

    private data class ResolvedReference(
        val id: UUID,
        val type: ChatEntityType,
        val label: String,
        val content: String
    )

    companion object {
        const val NEW_CONVERSATION_ID = "new"
    }
}
