package com.briefy.api.application.chat

import com.briefy.api.application.briefing.BriefingErrorResponse
import com.briefy.api.application.chat.tool.TopicLookupError
import com.briefy.api.application.chat.tool.TopicLookupRequest
import com.briefy.api.application.chat.tool.TopicLookupTool
import com.briefy.api.domain.knowledgegraph.briefing.BriefingStatus
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
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID
import java.util.function.Function

@Service
class ChatService(
    private val conversationRepository: ConversationRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val sourceRepository: SourceRepository,
    private val briefingRepository: BriefingRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val idGenerator: IdGenerator,
    private val aiAdapter: AiAdapter,
    private val topicLookupTool: TopicLookupTool,
    private val chatMemory: ChatMemory,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate,
    private val briefingChatHandler: BriefingChatHandler,
    @param:Value("\${chat.conversation.provider:google_genai}")
    private val chatProvider: String,
    @param:Value("\${chat.conversation.model:gemini-2.5-flash}")
    private val chatModel: String
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val defaultListLimit = 20
    private val maxListLimit = 100

    fun sendMessage(command: SendChatMessageCommand): Flux<ServerSentEvent<String>> {
        val userId = currentUserProvider.requireUserId()

        if (command.action != null) {
            val conversation = transactionTemplate.execute {
                resolveConversation(userId, command.conversationIdOrNew)
            } ?: error("Failed to resolve conversation")
            return briefingChatHandler.handle(conversation, command)
        }

        return sendConversationMessage(userId, command)
    }

    private fun sendConversationMessage(
        userId: UUID,
        command: SendChatMessageCommand
    ): Flux<ServerSentEvent<String>> {
        val preparedTurn = prepareTurn(userId, command)
        val assistantContent = StringBuilder()
        val memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build()
        val topicLookupCallback = buildTopicLookupCallback()

        return aiAdapter.streamWithAdvisors(
            provider = chatProvider,
            model = chatModel,
            userMessage = preparedTurn.userText,
            systemPrompt = preparedTurn.systemPrompt,
            useCase = "chat_conversation",
            advisors = listOf(memoryAdvisor),
            advisorParams = mapOf(ChatMemory.CONVERSATION_ID to preparedTurn.conversation.id.toString()),
            toolCallbacks = listOf(topicLookupCallback)
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
            chatMemory.clear(conversation.id.toString())
        }
    }

    fun persistBriefingResult(command: PersistBriefingResultCommand): ChatMessageResponse {
        val userId = currentUserProvider.requireUserId()

        return transactionTemplate.execute {
            val conversation = conversationRepository.findWithLockByIdAndUserId(command.conversationId, userId)
                ?: throw ChatConversationNotFoundException(command.conversationId)
            val briefing = briefingRepository.findByIdAndUserId(command.briefingId, userId)
                ?: throw ChatReferenceAccessException("briefing", command.briefingId)

            if (briefing.status != BriefingStatus.READY && briefing.status != BriefingStatus.FAILED) {
                throw InvalidChatRequestException(
                    "Briefing '${command.briefingId}' is not in a terminal state (status=${briefing.status})"
                )
            }

            val persistedMessageTypes = listOf(ChatMessageType.BRIEFING_RESULT, ChatMessageType.BRIEFING_ERROR)
            val existingPersistedMessage = chatMessageRepository
                .findFirstByConversationIdAndEntityIdAndTypeInOrderByCreatedAtAsc(
                    conversationId = conversation.id,
                    entityId = command.briefingId,
                    types = persistedMessageTypes
                )
            if (existingPersistedMessage != null) {
                return@execute toMessageResponse(existingPersistedMessage)
            }

            val isLinkedToConversation = chatMessageRepository.existsByConversationIdAndEntityTypeAndEntityId(
                conversationId = conversation.id,
                entityType = ChatEntityType.BRIEFING,
                entityId = command.briefingId
            )
            if (!isLinkedToConversation) {
                throw InvalidChatRequestException(
                    "Briefing '${command.briefingId}' is not linked to conversation '${conversation.id}'"
                )
            }

            val now = Instant.now()
            val isSuccess = briefing.status == BriefingStatus.READY

            val payload = if (isSuccess) {
                objectMapper.createObjectNode().apply {
                    put("briefingId", command.briefingId.toString())
                    put("title", briefing.title ?: "Briefing")
                    put("status", briefing.status.name.lowercase())
                }
            } else {
                buildBriefingErrorPayload(command.briefingId, briefing)
            }

            val message = ChatMessage(
                id = idGenerator.newId(),
                conversationId = conversation.id,
                role = ChatMessageRole.SYSTEM,
                type = if (isSuccess) ChatMessageType.BRIEFING_RESULT else ChatMessageType.BRIEFING_ERROR,
                content = null,
                payload = payload,
                entityType = ChatEntityType.BRIEFING,
                entityId = command.briefingId,
                createdAt = now
            )

            conversation.touch(now)
            conversationRepository.save(conversation)
            chatMessageRepository.save(message)

            toMessageResponse(message)
        } ?: error("Failed to persist briefing result")
    }

    private fun buildBriefingErrorPayload(briefingId: UUID, briefing: Briefing): JsonNode {
        val payload = objectMapper.createObjectNode().apply {
            put("briefingId", briefingId.toString())
            put("status", briefing.status.name.lowercase())
        }
        val error = parseBriefingError(briefing.errorJson)

        payload.put("message", error?.message ?: briefing.errorJson ?: "Briefing generation failed")
        payload.put("retryable", error?.retryable ?: false)
        error?.code?.let { payload.put("code", it) }
        error?.details?.takeIf { it.isNotEmpty() }?.let { details ->
            payload.set<JsonNode>("details", objectMapper.valueToTree(details))
        }
        return payload
    }

    private fun parseBriefingError(rawError: String?): BriefingErrorResponse? {
        if (rawError.isNullOrBlank()) {
            return null
        }

        return runCatching {
            objectMapper.readValue(rawError, BriefingErrorResponse::class.java)
        }.getOrNull()
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
                systemPrompt = buildSystemPrompt(resolvedReferences),
                userText = trimmedText
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
            appendLine("The user saves sources and organizes them into topics inside Briefy.")
            appendLine("Answer clearly and directly.")
            appendLine("When the user asks about their library, topics, sources, reading habits, or interests, use your tools to look up real data before answering. Never ask the user for permission to use a tool — just use it.")
            appendLine("If referenced content is provided below, prioritize it and say when the answer depends on that context.")
            appendLine("For questions unrelated to the user's library, answer from general knowledge.")
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

    private fun buildTopicLookupCallback() = FunctionToolCallback.builder(
        "topic_lookup",
        Function<TopicLookupToolRequest, String> { request ->
            executeTopicLookup(request)
        }
    )
        .description(
            "Browse the user's topics and the sources within them. " +
                "Without topicId: lists topics filtered by status (ACTIVE/SUGGESTED/ARCHIVED, default ACTIVE) and optional name filter. " +
                "With topicId: returns the topic details and its linked sources with title, URL, type, read status, and word count. " +
                "Set includeSourceIds=true in list mode to also get source IDs per topic."
        )
        .inputType(TopicLookupToolRequest::class.java)
        .build()

    private fun executeTopicLookup(request: TopicLookupToolRequest): String {
        val topicId = request.topicId?.trim()?.takeIf { it.isNotBlank() }?.let { rawTopicId ->
            runCatching { UUID.fromString(rawTopicId) }.getOrElse {
                return objectMapper.writeValueAsString(TopicLookupError("Invalid 'topicId' argument for topic_lookup."))
            }
        }

        return objectMapper.writeValueAsString(
            topicLookupTool.lookup(
                TopicLookupRequest(
                    topicId = topicId,
                    filter = request.filter?.trim()?.takeIf { it.isNotBlank() },
                    includeSourceIds = request.includeSourceIds ?: false,
                    status = request.status
                )
            )
        )
    }

    private fun toMessageResponse(message: ChatMessage): ChatMessageResponse {
        return ChatMessageResponse(
            id = message.id,
            role = message.role.name.lowercase(),
            type = message.type.name.lowercase(),
            content = message.content,
            payload = toResponsePayload(message.payload),
            contentReferences = extractContentReferences(message.payload),
            entityType = message.entityType?.name?.lowercase(),
            entityId = message.entityId,
            createdAt = message.createdAt
        )
    }

    private fun toResponsePayload(payload: JsonNode?): Any? {
        return payload?.let { objectMapper.convertValue(it, Any::class.java) }
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
        val systemPrompt: String,
        val userText: String
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

    private data class TopicLookupToolRequest(
        val topicId: String? = null,
        val filter: String? = null,
        val includeSourceIds: Boolean? = null,
        val status: String? = null
    )
}
