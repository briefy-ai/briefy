package com.briefy.api.api

import com.briefy.api.application.briefing.tool.WebFetchTool
import com.briefy.api.domain.chat.ChatMessageRepository
import com.briefy.api.domain.chat.ConversationRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingRepository
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import reactor.core.publisher.Flux
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "chat.conversation.tools.web-search.enabled=false",
        "chat.conversation.tools.web-fetch.enabled=true",
        "briefing.execution.tools.web-search.enabled=false",
        "briefing.execution.tools.web-fetch.enabled=false"
    ]
)
class ChatControllerWebFetchOnlyTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sourceRepository: SourceRepository

    @Autowired
    lateinit var briefingRepository: BriefingRepository

    @Autowired
    lateinit var topicRepository: TopicRepository

    @Autowired
    lateinit var topicLinkRepository: TopicLinkRepository

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

    @MockitoBean
    lateinit var webFetchTool: WebFetchTool

    private val testUserId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @BeforeEach
    fun setupCurrentUser() {
        jdbcTemplate.execute("DELETE FROM spring_ai_chat_memory")
        chatMessageRepository.deleteAll()
        conversationRepository.deleteAll()
        topicLinkRepository.deleteAll()
        topicRepository.deleteAll()
        briefingRepository.deleteAll()
        sourceRepository.deleteAll()
        `when`(currentUserProvider.requireUserId()).thenReturn(testUserId)
    }

    @Test
    fun `fetch only chat prompt does not require unavailable web search`() {
        `when`(
            aiAdapter.streamWithAdvisors(
                eq("google_genai"),
                eq("gemini-2.5-flash"),
                any(),
                anyOrNull(),
                eq("chat_conversation"),
                anyOrNull(),
                any(),
                any(),
                any()
            )
        ).thenAnswer { invocation ->
            val systemPrompt = invocation.getArgument<String?>(3).orEmpty()

            assertTrue(systemPrompt.contains("Use `web_fetch` selectively for external factual questions when you already have a direct URL to read."))
            assertFalse(systemPrompt.contains("Use `web_fetch` only after `web_search`"))

            Flux.just("Fetch", " only", " prompt")
        }

        val createResult = mockMvc.perform(
            post("/api/chat/conversations/new/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "text": "Read this URL for me",
                      "contentReferences": []
                    }
                    """.trimIndent()
                )
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(createResult))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(containsString("Fetch only prompt")))
    }
}
