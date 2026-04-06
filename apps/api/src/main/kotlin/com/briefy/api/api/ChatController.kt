package com.briefy.api.api

import com.briefy.api.application.chat.ChatConversationPageResponse
import com.briefy.api.application.chat.ChatConversationResponse
import com.briefy.api.application.chat.ChatContentReferenceInput
import com.briefy.api.application.chat.ChatService
import com.briefy.api.application.chat.SendChatMessageCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.util.UUID

@RestController
@RequestMapping("/api/chat/conversations")
class ChatController(
    private val chatService: ChatService
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)

    @PostMapping(
        "/new/messages",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    fun sendFirstMessage(
        @Valid @RequestBody request: SendChatMessageRequest
    ): Flux<ServerSentEvent<String>> {
        logger.info(
            "[controller] Chat first message request received referenceCount={}",
            request.contentReferences.size
        )
        return chatService.sendMessage(
            SendChatMessageCommand(
                conversationIdOrNew = ChatService.NEW_CONVERSATION_ID,
                text = request.text,
                contentReferences = request.contentReferences.map { it.toCommand() }
            )
        )
    }

    @PostMapping(
        "/{id}/messages",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    fun sendMessage(
        @PathVariable id: String,
        @Valid @RequestBody request: SendChatMessageRequest
    ): Flux<ServerSentEvent<String>> {
        logger.info(
            "[controller] Chat message request received conversationId={} referenceCount={}",
            id,
            request.contentReferences.size
        )
        return chatService.sendMessage(
            SendChatMessageCommand(
                conversationIdOrNew = id,
                text = request.text,
                contentReferences = request.contentReferences.map { it.toCommand() }
            )
        )
    }

    @GetMapping
    fun listConversations(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) cursor: String?
    ): ResponseEntity<ChatConversationPageResponse> {
        val page = chatService.listConversations(limit, cursor)
        return ResponseEntity.ok(page)
    }

    @GetMapping("/{id}")
    fun getConversation(@PathVariable id: UUID): ResponseEntity<ChatConversationResponse> {
        return ResponseEntity.ok(chatService.getConversation(id))
    }

    @DeleteMapping("/{id}")
    fun deleteConversation(@PathVariable id: UUID): ResponseEntity<Void> {
        chatService.deleteConversation(id)
        return ResponseEntity.noContent().build()
    }
}

data class SendChatMessageRequest(
    @field:NotBlank(message = "text is required")
    val text: String,
    @field:Valid
    val contentReferences: List<ChatContentReferenceRequest> = emptyList()
)

data class ChatContentReferenceRequest(
    val id: UUID,
    @field:NotBlank(message = "type is required")
    val type: String
) {
    fun toCommand(): ChatContentReferenceInput {
        return ChatContentReferenceInput(
            id = id,
            type = type
        )
    }
}
