package com.briefy.api.application.briefing

import com.briefy.api.application.briefing.tool.SourceLookupResponse
import com.briefy.api.application.briefing.tool.SourceLookupResult
import com.briefy.api.application.briefing.tool.SourceLookupTool
import com.briefy.api.application.briefing.tool.ToolErrorCode
import com.briefy.api.application.briefing.tool.ToolResult
import com.briefy.api.application.briefing.tool.WebFetchResponse
import com.briefy.api.application.briefing.tool.WebFetchTool
import com.briefy.api.application.briefing.tool.WebSearchResponse
import com.briefy.api.application.briefing.tool.WebSearchResult
import com.briefy.api.application.briefing.tool.WebSearchTool
import com.briefy.api.infrastructure.ai.AiAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.ai.tool.ToolCallback
import java.util.UUID

class AiSubagentExecutionRunnerTest {

    private val objectMapper = ObjectMapper()
    private val aiAdapter = mock<AiAdapter>()
    private val webSearchTool = mock<WebSearchTool>()
    private val webFetchTool = mock<WebFetchTool>()
    private val sourceLookupTool = mock<SourceLookupTool>()

    private val config = AiSubagentExecutionRunner.AiRunnerConfig(
        provider = "google_genai",
        model = "gemini-2.5-flash"
    )

    private val runner = AiSubagentExecutionRunner(
        aiAdapter = aiAdapter,
        webSearchTool = webSearchTool,
        webFetchTool = webFetchTool,
        sourceLookupTool = sourceLookupTool,
        objectMapper = objectMapper,
        config = config
    )

    private val baseContext = SubagentExecutionContext(
        briefingId = UUID.randomUUID(),
        briefingRunId = UUID.randomUUID(),
        subagentRunId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        attempt = 1,
        maxAttempts = 3,
        personaKey = "market_analyst",
        personaName = "Market Analyst",
        task = "Analyze market trends for AI adoption in healthcare",
        sources = listOf(
            BriefingSourceInput(
                sourceId = UUID.randomUUID(),
                title = "Healthcare AI Report 2026",
                url = "https://example.com/report",
                text = "AI adoption in healthcare is growing at 25% annually..."
            )
        )
    )

    @Test
    fun `produces output directly from sources when native tool path returns output block`() {
        whenever(aiAdapter.completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(
                """Based on the internal sources, here is my analysis:

```output
## Market Analysis: AI in Healthcare

AI adoption in healthcare is growing at 25% annually.
```"""
            )

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        assertTrue(succeeded.curatedText.contains("AI adoption in healthcare"))
        assertNotNull(succeeded.sourceIdsUsedJson)
        assertNotNull(succeeded.toolStatsJson)
        assertEquals(0, objectMapper.readTree(succeeded.toolStatsJson).get("toolCallCount").asInt())
        verify(aiAdapter, times(1)).completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any())
        verifyNoInteractions(webSearchTool)
    }

    @Test
    fun `uses source lookup callback and tracks returned source ids`() {
        val lookedUpSourceId = UUID.randomUUID()
        whenever(
            sourceLookupTool.lookup(
                query = "related AI regulation sources",
                sourceId = null,
                limit = 3,
                userId = baseContext.userId,
                excludeSourceIds = setOf(baseContext.sources.first().sourceId)
            )
        ).thenReturn(
            ToolResult.Success(
                SourceLookupResponse(
                    results = listOf(
                        SourceLookupResult(
                            sourceId = lookedUpSourceId,
                            title = "Related source",
                            url = "https://example.com/related",
                            score = 0.88,
                            wordCount = 240,
                            excerpt = "Helpful excerpt"
                        )
                    ),
                    mode = "query",
                    query = "related AI regulation sources",
                    sourceId = null
                )
            )
        )
        whenever(aiAdapter.completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenAnswer { invocation ->
                toolCallback(invocation.getArgument(5), "source_lookup")
                    .call("""{"query":"related AI regulation sources","limit":3}""")

                """```output
Analysis using related internal sources.
```"""
            }

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        val sourceIds = objectMapper.readTree(succeeded.sourceIdsUsedJson)
        assertEquals(1, objectMapper.readTree(succeeded.toolStatsJson).get("toolCallCount").asInt())
        assertEquals(2, sourceIds.size())
        assertTrue(sourceIds.any { it.asText() == lookedUpSourceId.toString() })
        verify(sourceLookupTool).lookup(
            query = "related AI regulation sources",
            sourceId = null,
            limit = 3,
            userId = baseContext.userId,
            excludeSourceIds = setOf(baseContext.sources.first().sourceId)
        )
    }

    @Test
    fun `uses web search callback when tool is invoked`() {
        whenever(webSearchTool.search(any(), any())).thenReturn(
            ToolResult.Success(
                WebSearchResponse(
                    results = listOf(
                        WebSearchResult("AI Health 2026", "https://news.example.com/ai-health", "New trends...")
                    ),
                    query = "healthcare AI adoption 2026 trends"
                )
            )
        )
        whenever(aiAdapter.completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenAnswer { invocation ->
                toolCallback(invocation.getArgument(5), "web_search")
                    .call("""{"query":"healthcare AI adoption 2026 trends"}""")

                """```output
## Healthcare AI Market Analysis

The market is expanding rapidly with key developments in 2026.
```"""
            }

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        assertTrue(succeeded.curatedText.contains("Healthcare AI"))
        assertNotNull(succeeded.referencesUsedJson)
        assertEquals(1, objectMapper.readTree(succeeded.toolStatsJson).get("toolCallCount").asInt())
        verify(webSearchTool).search(eq("healthcare AI adoption 2026 trends"), eq(5))
    }

    @Test
    fun `uses web fetch callback when tool is invoked`() {
        whenever(webFetchTool.fetch(any())).thenReturn(
            ToolResult.Success(
                WebFetchResponse(
                    url = "https://example.com/article",
                    title = "AI Article",
                    content = "Detailed article content about AI...",
                    contentLengthBytes = 1000
                )
            )
        )
        whenever(aiAdapter.completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenAnswer { invocation ->
                toolCallback(invocation.getArgument(5), "web_fetch")
                    .call("""{"url":"https://example.com/article"}""")

                """```output
Analysis based on fetched content.
```"""
            }

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        assertEquals(1, objectMapper.readTree(succeeded.toolStatsJson).get("toolCallCount").asInt())
        verify(webFetchTool).fetch(eq("https://example.com/article"))
    }

    @Test
    fun `returns EmptyOutput when native tool path produces empty output block`() {
        whenever(aiAdapter.completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(
                """```output

```"""
            )

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.EmptyOutput)
    }

    @Test
    fun `returns Failed on retryable tool error`() {
        whenever(webSearchTool.search(any(), any())).thenReturn(
            ToolResult.Error(ToolErrorCode.HTTP_429, "Rate limited")
        )
        whenever(aiAdapter.completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenAnswer { invocation ->
                toolCallback(invocation.getArgument(5), "web_search")
                    .call("""{"query":"test query"}""")
            }

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("http_429", failed.errorCode)
        assertTrue(failed.retryable)
    }

    @Test
    fun `continues on non retryable tool error`() {
        whenever(webFetchTool.fetch(any())).thenReturn(
            ToolResult.Error(ToolErrorCode.SSRF_BLOCKED, "SSRF blocked")
        )
        whenever(aiAdapter.completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenAnswer { invocation ->
                toolCallback(invocation.getArgument(5), "web_fetch")
                    .call("""{"url":"http://internal.server"}""")

                """```output
Analysis without the blocked URL.
```"""
            }

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
    }

    @Test
    fun `counts all native tool callback invocations in tool stats`() {
        whenever(webSearchTool.search(any(), any())).thenReturn(
            ToolResult.Success(
                WebSearchResponse(
                    results = listOf(WebSearchResult("Result", "https://example.com", "snippet")),
                    query = "query"
                )
            )
        )
        whenever(aiAdapter.completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenAnswer { invocation ->
                val callback = toolCallback(invocation.getArgument(5), "web_search")
                callback.call("""{"query":"query 1"}""")
                callback.call("""{"query":"query 2"}""")
                callback.call("""{"query":"query 3"}""")
                callback.call("""{"query":"query 4"}""")

                """```output
Repeated-search analysis.
```"""
            }

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        val toolStats = objectMapper.readTree(succeeded.toolStatsJson)
        assertEquals(4, toolStats.get("toolCallCount").asInt())
        verify(webSearchTool, times(4)).search(any(), any())
    }

    @Test
    fun `handles llm exception as retryable failure`() {
        whenever(aiAdapter.completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenThrow(RuntimeException("Connection timeout"))

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("runner_error", failed.errorCode)
        assertTrue(failed.retryable)
    }

    @Test
    fun `works without tools when all tool beans are null`() {
        val noToolsRunner = AiSubagentExecutionRunner(
            aiAdapter = aiAdapter,
            webSearchTool = null,
            webFetchTool = null,
            sourceLookupTool = null,
            objectMapper = objectMapper,
            config = config
        )
        whenever(aiAdapter.complete(any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(
                """```output
Output without tools.
```"""
            )

        val result = noToolsRunner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        verify(aiAdapter).complete(any(), any(), any(), anyOrNull(), anyOrNull())
        verify(aiAdapter, never()).completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any())
        verifyNoInteractions(webSearchTool)
    }

    @Test
    fun `graceful fallback when model returns plain text`() {
        whenever(aiAdapter.completeWithTools(any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn("This is my analysis of the healthcare market. AI adoption is growing rapidly.")

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        assertTrue(succeeded.curatedText.contains("healthcare market"))
    }

    private fun toolCallback(callbacks: List<ToolCallback>, name: String): ToolCallback {
        return callbacks.first { it.toolDefinition.name() == name }
    }
}
