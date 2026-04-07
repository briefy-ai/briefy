package com.briefy.api.api

import com.briefy.api.domain.chat.ChatEntityType
import com.briefy.api.domain.chat.ChatMessage
import com.briefy.api.domain.chat.ChatMessageRole
import com.briefy.api.domain.chat.ChatMessageType
import com.briefy.api.domain.chat.ChatMessageRepository
import com.briefy.api.domain.chat.Conversation
import com.briefy.api.domain.chat.ConversationRepository
import com.briefy.api.domain.knowledgegraph.briefing.Briefing
import com.briefy.api.domain.knowledgegraph.briefing.BriefingEnrichmentIntent
import com.briefy.api.domain.knowledgegraph.briefing.BriefingRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingStatus
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sourceRepository: SourceRepository

    @Autowired
    lateinit var briefingRepository: BriefingRepository

    @Autowired
    lateinit var conversationRepository: ConversationRepository

    @Autowired
    lateinit var chatMessageRepository: ChatMessageRepository

    @Autowired
    lateinit var chatMemory: ChatMemory

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    lateinit var currentUserProvider: CurrentUserProvider

    @MockitoBean
    lateinit var aiAdapter: AiAdapter

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val testUserId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @BeforeEach
    fun setupCurrentUser() {
        jdbcTemplate.execute("DELETE FROM spring_ai_chat_memory")
        chatMessageRepository.deleteAll()
        conversationRepository.deleteAll()
        briefingRepository.deleteAll()
        sourceRepository.deleteAll()
        `when`(currentUserProvider.requireUserId()).thenReturn(testUserId)
    }

    @Test
    fun `chat conversation lifecycle works through controller`() {
        val source = createActiveSource("https://example.com/chat-source", "Referenced source content")
        val briefing = createReadyBriefing("Referenced briefing content")
        `when`(
            aiAdapter.streamWithAdvisors(
                eq("google_genai"),
                eq("gemini-2.5-flash"),
                any(),
                anyOrNull(),
                eq("chat_conversation"),
                any(),
                any()
            )
        ).thenReturn(Flux.just("Hello", " from", " model"))

        val createResult = mockMvc.perform(
            post("/api/chat/conversations/new/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "text": "Summarize these references",
                      "contentReferences": [
                        { "id": "${source.id}", "type": "source" },
                        { "id": "${briefing.id}", "type": "briefing" }
                      ]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        val streamResponse = mockMvc.perform(asyncDispatch(createResult))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(containsString("\"type\":\"token\"")))
            .andExpect(content().string(containsString("\"type\":\"message\"")))
            .andExpect(content().string(containsString("Hello from model")))
            .andReturn()

        val conversationId = extractConversationId(streamResponse.response.contentAsString)

        mockMvc.perform(get("/api/chat/conversations/$conversationId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(conversationId.toString()))
            .andExpect(jsonPath("$.messages.length()").value(2))
            .andExpect(jsonPath("$.messages[0].role").value("user"))
            .andExpect(jsonPath("$.messages[0].type").value("user_text"))
            .andExpect(jsonPath("$.messages[0].contentReferences.length()").value(2))
            .andExpect(jsonPath("$.messages[0].entityType").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.messages[1].role").value("assistant"))
            .andExpect(jsonPath("$.messages[1].type").value("assistant_text"))
            .andExpect(jsonPath("$.messages[1].content").value("Hello from model"))

        mockMvc.perform(get("/api/chat/conversations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(conversationId.toString()))
            .andExpect(jsonPath("$.items[0].lastMessagePreview").value("Hello from model"))

        mockMvc.perform(delete("/api/chat/conversations/$conversationId"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/chat/conversations/$conversationId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `deleting a conversation clears spring ai chat memory`() {
        val now = Instant.now()
        val conversation = conversationRepository.save(
            Conversation(
                id = UUID.randomUUID(),
                userId = testUserId,
                createdAt = now,
                updatedAt = now
            )
        )
        chatMemory.add(conversation.id.toString(), org.springframework.ai.chat.messages.UserMessage("Remember this"))

        mockMvc.perform(delete("/api/chat/conversations/${conversation.id}"))
            .andExpect(status().isNoContent)

        assertTrue(chatMemory.get(conversation.id.toString()).isEmpty())
    }

    @Test
    fun `persist briefing result rejects briefing not linked to conversation`() {
        val conversation = createConversation()
        val briefing = createReadyBriefing("Referenced briefing content")

        mockMvc.perform(
            post("/api/chat/conversations/${conversation.id}/briefing-result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "briefingId": "${briefing.id}" }""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("not linked to conversation")))
    }

    @Test
    fun `persist briefing result stores parsed failed briefing payload`() {
        val conversation = createConversation()
        val briefing = createFailedBriefing(
            """
            {
              "code": "planner_failed",
              "message": "Planner crashed",
              "retryable": false,
              "details": {
                "step": "planning"
              }
            }
            """.trimIndent()
        )
        linkBriefingToConversation(conversation.id, briefing.id)

        mockMvc.perform(
            post("/api/chat/conversations/${conversation.id}/briefing-result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "briefingId": "${briefing.id}" }""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("briefing_error"))
            .andExpect(jsonPath("$.payload.briefingId").value(briefing.id.toString()))
            .andExpect(jsonPath("$.payload.status").value("failed"))
            .andExpect(jsonPath("$.payload.message").value("Planner crashed"))
            .andExpect(jsonPath("$.payload.retryable").value(false))
            .andExpect(jsonPath("$.payload.code").value("planner_failed"))
            .andExpect(jsonPath("$.payload.details.step").value("planning"))
    }

    @Test
    fun `approve plan action does not persist user action when approval fails`() {
        val conversation = createConversation()
        val briefing = createReadyBriefing("Already completed briefing")

        val requestResult = mockMvc.perform(
            post("/api/chat/conversations/${conversation.id}/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "text": "Approved plan",
                      "contentReferences": [],
                      "action": {
                        "type": "approve_plan",
                        "briefingId": "${briefing.id}"
                      }
                    }
                    """.trimIndent()
                )
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(requestResult))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("Can only approve plans in plan_pending_approval status")))

        assertTrue(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.id).isEmpty())
    }

    private fun createActiveSource(url: String, text: String): Source {
        return sourceRepository.save(
            Source(
                id = UUID.randomUUID(),
                url = Url.from(url),
                status = SourceStatus.ACTIVE,
                content = Content.from(text),
                metadata = Metadata.from(
                    title = "Chat Source",
                    author = "Author",
                    publishedDate = Instant.parse("2024-01-15T10:00:00Z"),
                    platform = "web",
                    wordCount = Content.countWords(text),
                    aiFormatted = false,
                    extractionProvider = "manual"
                ),
                userId = testUserId
            )
        )
    }

    private fun createReadyBriefing(contentMarkdown: String): Briefing {
        val now = Instant.now()
        val briefing = Briefing.create(
            id = UUID.randomUUID(),
            userId = testUserId,
            enrichmentIntent = BriefingEnrichmentIntent.DEEP_DIVE,
            now = now
        )
        briefing.contentMarkdown = contentMarkdown
        briefing.title = "Stored Briefing"
        briefing.status = BriefingStatus.READY
        briefing.generationCompletedAt = now
        briefing.updatedAt = now
        return briefingRepository.save(briefing)
    }

    private fun createFailedBriefing(errorJson: String): Briefing {
        val now = Instant.now()
        val briefing = Briefing.create(
            id = UUID.randomUUID(),
            userId = testUserId,
            enrichmentIntent = BriefingEnrichmentIntent.DEEP_DIVE,
            now = now
        )
        briefing.status = BriefingStatus.FAILED
        briefing.errorJson = errorJson
        briefing.failedAt = now
        briefing.updatedAt = now
        return briefingRepository.save(briefing)
    }

    private fun createConversation(): Conversation {
        val now = Instant.now()
        return conversationRepository.save(
            Conversation(
                id = UUID.randomUUID(),
                userId = testUserId,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun linkBriefingToConversation(conversationId: UUID, briefingId: UUID) {
        chatMessageRepository.save(
            ChatMessage(
                id = UUID.randomUUID(),
                conversationId = conversationId,
                role = ChatMessageRole.SYSTEM,
                type = ChatMessageType.BRIEFING_PLAN,
                payload = objectMapper.createObjectNode().put("id", briefingId.toString()),
                entityType = ChatEntityType.BRIEFING,
                entityId = briefingId,
                createdAt = Instant.now()
            )
        )
    }

    private fun extractConversationId(streamBody: String): UUID {
        val payload = streamBody.lineSequence()
            .first { it.startsWith("data:") && it.contains("\"type\":\"message\"") }
            .removePrefix("data:")
            .trim()
        return UUID.fromString(objectMapper.readTree(payload).path("conversationId").asText())
    }
}
