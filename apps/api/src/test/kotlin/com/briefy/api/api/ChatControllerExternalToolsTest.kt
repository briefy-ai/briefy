package com.briefy.api.api

import com.briefy.api.application.briefing.tool.ToolErrorCode
import com.briefy.api.application.briefing.tool.ToolResult
import com.briefy.api.application.briefing.tool.WebFetchResponse
import com.briefy.api.application.briefing.tool.WebFetchTool
import com.briefy.api.application.briefing.tool.WebSearchResponse
import com.briefy.api.application.briefing.tool.WebSearchResult
import com.briefy.api.application.briefing.tool.WebSearchTool
import com.briefy.api.domain.chat.ChatMessageRepository
import com.briefy.api.domain.chat.ConversationRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingRepository
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.tool.ToolCallback
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
        "chat.conversation.tools.web-search.enabled=true",
        "chat.conversation.tools.web-fetch.enabled=true",
        "briefing.execution.tools.web-search.enabled=false",
        "briefing.execution.tools.web-fetch.enabled=false",
        "briefing.execution.tools.web-search.brave-api-key=test-key"
    ]
)
class ChatControllerExternalToolsTest {

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
    lateinit var webSearchTool: WebSearchTool

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
    fun `chat registers web search callback on streaming path`() {
        `when`(webSearchTool.search(eq("latest ai news"), eq(10))).thenReturn(
            ToolResult.Success(
                WebSearchResponse(
                    query = "latest ai news",
                    results = listOf(
                        WebSearchResult(
                            title = "AI roundup",
                            url = "https://example.com/ai-roundup",
                            snippet = "A current summary."
                        )
                    )
                )
            )
        )

        `when`(
            aiAdapter.streamWithAdvisors(
                eq("google_genai"),
                eq("gemini-3.1-flash-lite-preview"),
                any(),
                anyOrNull(),
                eq("chat_conversation"),
                anyOrNull(),
                any(),
                any(),
                any()
            )
        ).thenAnswer { invocation ->
            val callbacks = invocation.getArgument<List<ToolCallback>>(8)
            val toolNames = callbacks.map { it.toolDefinition.name() }
            assertTrue(toolNames.containsAll(listOf("topic_lookup", "source_lookup", "web_search", "web_fetch")))

            val payload = callbacks.single { it.toolDefinition.name() == "web_search" }
                .call("""{"query":"latest ai news","maxResults":99}""")

            assertTrue(payload.contains("[web_search query: latest ai news]"))
            assertTrue(payload.contains("https://example.com/ai-roundup"))

            Flux.just("Web", " search", " works")
        }

        val createResult = mockMvc.perform(
            post("/api/chat/conversations/new/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "text": "What happened in AI recently?",
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
            .andExpect(content().string(containsString("Web search works")))
    }

    @Test
    fun `chat registers web fetch callback and keeps streaming on tool error`() {
        `when`(webFetchTool.fetch(eq("https://example.com/article"))).thenReturn(
            ToolResult.Error(ToolErrorCode.TIMEOUT, "Timeout fetching https://example.com/article")
        )

        `when`(
            aiAdapter.streamWithAdvisors(
                eq("google_genai"),
                eq("gemini-3.1-flash-lite-preview"),
                any(),
                anyOrNull(),
                eq("chat_conversation"),
                anyOrNull(),
                any(),
                any(),
                any()
            )
        ).thenAnswer { invocation ->
            val callbacks = invocation.getArgument<List<ToolCallback>>(8)
            val payload = callbacks.single { it.toolDefinition.name() == "web_fetch" }
                .call("""{"url":"https://example.com/article"}""")

            assertTrue(payload.contains("web_fetch failed"))
            assertTrue(payload.contains("Timeout fetching https://example.com/article"))

            Flux.just("Web", " fetch", " works")
        }

        val createResult = mockMvc.perform(
            post("/api/chat/conversations/new/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "text": "Read this article",
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
            .andExpect(content().string(containsString("Web fetch works")))
    }
}
