package com.briefy.api.application.chat

import com.briefy.api.application.briefing.BriefingService
import com.briefy.api.application.briefing.CreateBriefingCommand
import com.briefy.api.domain.chat.ChatEntityType
import com.briefy.api.domain.chat.ChatMessage
import com.briefy.api.domain.chat.ChatMessageRepository
import com.briefy.api.domain.chat.ChatMessageRole
import com.briefy.api.domain.chat.ChatMessageType
import com.briefy.api.domain.chat.Conversation
import com.briefy.api.domain.chat.ConversationRepository
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

@Component
class BriefingChatHandler(
    private val briefingService: BriefingService,
    private val chatMessageRepository: ChatMessageRepository,
    private val conversationRepository: ConversationRepository,
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(BriefingChatHandler::class.java)

    fun handle(
        conversation: Conversation,
        command: SendChatMessageCommand
    ): Flux<ServerSentEvent<String>> {
        val action = command.action ?: return Flux.error(InvalidChatRequestException("Missing action"))

        return when (action.type) {
            "create_briefing" -> handleCreateBriefing(conversation, command, action)
            "approve_plan" -> handleApprovePlan(conversation, command, action)
            "retry_briefing" -> handleRetryBriefing(conversation, command, action)
            else -> Flux.error(InvalidChatRequestException("Unknown action type: ${action.type}"))
        }
    }

    private fun handleCreateBriefing(
        conversation: Conversation,
        command: SendChatMessageCommand,
        action: ChatAction
    ): Flux<ServerSentEvent<String>> {
        val sourceIds = action.sourceIds
            ?: return Flux.error(InvalidChatRequestException("sourceIds required for create_briefing"))
        val enrichmentIntent = action.enrichmentIntent
            ?: return Flux.error(InvalidChatRequestException("enrichmentIntent required for create_briefing"))

        return Mono.fromCallable {
            transactionTemplate.execute {
                val now = Instant.now()
                val userActionMsg = persistUserAction(conversation, command.text, action, now)

                val briefing = briefingService.createBriefing(
                    CreateBriefingCommand(
                        sourceIds = sourceIds,
                        enrichmentIntent = enrichmentIntent
                    )
                )

                val planPayload = objectMapper.valueToTree<JsonNode>(briefing)
                val planMsg = ChatMessage(
                    id = idGenerator.newId(),
                    conversationId = conversation.id,
                    role = ChatMessageRole.SYSTEM,
                    type = ChatMessageType.BRIEFING_PLAN,
                    content = null,
                    payload = planPayload,
                    entityType = ChatEntityType.BRIEFING,
                    entityId = briefing.id,
                    createdAt = Instant.now()
                )
                chatMessageRepository.save(planMsg)

                conversation.touch(Instant.now())
                conversationRepository.save(conversation)

                logger.info(
                    "[briefing-chat] Created briefing via chat conversationId={} briefingId={}",
                    conversation.id, briefing.id
                )

                listOf(toMessageResponse(userActionMsg), toMessageResponse(planMsg))
            } ?: error("Failed to handle create_briefing")
        }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { messages -> emitBriefingAction(conversation.id, messages) }
    }

    private fun handleApprovePlan(
        conversation: Conversation,
        command: SendChatMessageCommand,
        action: ChatAction
    ): Flux<ServerSentEvent<String>> {
        val briefingId = action.briefingId
            ?: return Flux.error(InvalidChatRequestException("briefingId required for approve_plan"))

        return Mono.fromCallable {
            val messages = transactionTemplate.execute {
                val now = Instant.now()
                val userActionMsg = persistUserAction(conversation, command.text, action, now)
                briefingService.approvePlan(briefingId)
                conversation.touch(now)
                conversationRepository.save(conversation)
                listOf(toMessageResponse(userActionMsg))
            } ?: error("Failed to approve plan")

            logger.info(
                "[briefing-chat] Approved plan via chat conversationId={} briefingId={}",
                conversation.id, briefingId
            )
            messages
        }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { messages -> emitBriefingAction(conversation.id, messages) }
    }

    private fun handleRetryBriefing(
        conversation: Conversation,
        command: SendChatMessageCommand,
        action: ChatAction
    ): Flux<ServerSentEvent<String>> {
        val briefingId = action.briefingId
            ?: return Flux.error(InvalidChatRequestException("briefingId required for retry_briefing"))

        return Mono.fromCallable {
            val messages = transactionTemplate.execute {
                val now = Instant.now()
                val userActionMsg = persistUserAction(conversation, command.text, action, now)
                briefingService.retryBriefing(briefingId)
                conversation.touch(now)
                conversationRepository.save(conversation)
                listOf(toMessageResponse(userActionMsg))
            } ?: error("Failed to retry briefing")

            logger.info(
                "[briefing-chat] Retried briefing via chat conversationId={} briefingId={}",
                conversation.id, briefingId
            )
            messages
        }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { messages -> emitBriefingAction(conversation.id, messages) }
    }

    private fun persistUserAction(
        conversation: Conversation,
        text: String,
        action: ChatAction,
        now: Instant
    ): ChatMessage {
        val actionPayload = objectMapper.createObjectNode().apply {
            put("actionType", action.type)
            put("label", text)
            action.briefingId?.let { put("briefingId", it.toString()) }
            action.enrichmentIntent?.let { put("enrichmentIntent", it) }
            action.sourceIds?.let { ids ->
                putArray("sourceIds").apply { ids.forEach { add(it.toString()) } }
            }
        }

        val message = ChatMessage(
            id = idGenerator.newId(),
            conversationId = conversation.id,
            role = ChatMessageRole.USER,
            type = ChatMessageType.USER_ACTION,
            content = text,
            payload = actionPayload,
            entityType = action.briefingId?.let { ChatEntityType.BRIEFING },
            entityId = action.briefingId,
            createdAt = now
        )

        return chatMessageRepository.save(message)
    }

    private fun emitBriefingAction(
        conversationId: UUID,
        messages: List<ChatMessageResponse>
    ): Flux<ServerSentEvent<String>> {
        val event = ChatBriefingActionStreamEvent(
            conversationId = conversationId,
            messages = messages
        )
        return Flux.just(
            ServerSentEvent.builder(objectMapper.writeValueAsString(event))
                .id(conversationId.toString())
                .build()
        )
    }

    private fun toMessageResponse(message: ChatMessage): ChatMessageResponse {
        return ChatMessageResponse(
            id = message.id,
            role = message.role.name.lowercase(),
            type = message.type.name.lowercase(),
            content = message.content,
            payload = toResponsePayload(message.payload),
            contentReferences = emptyList(),
            entityType = message.entityType?.name?.lowercase(),
            entityId = message.entityId,
            createdAt = message.createdAt
        )
    }

    private fun toResponsePayload(payload: JsonNode?): Any? {
        return payload?.let { objectMapper.convertValue(it, Any::class.java) }
    }
}
