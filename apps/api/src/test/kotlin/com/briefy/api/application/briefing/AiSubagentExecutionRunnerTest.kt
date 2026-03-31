package com.briefy.api.application.briefing

import com.briefy.api.application.briefing.tool.*
import com.briefy.api.infrastructure.ai.AiAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.UUID

class AiSubagentExecutionRunnerTest {

    private val objectMapper = ObjectMapper()
    private val aiAdapter = mock<AiAdapter>()
    private val webSearchTool = mock<WebSearchTool>()
    private val webFetchTool = mock<WebFetchTool>()
    private val sourceLookupTool = mock<SourceLookupTool>()

    private val config = AiSubagentExecutionRunner.AiRunnerConfig(
        provider = "google_genai",
        model = "gemini-2.5-flash",
        maxToolCalls = 8
    )
    private val noopTracer = OpenTelemetry.noop().getTracer("test")

    private val runner = AiSubagentExecutionRunner(
        aiAdapter = aiAdapter,
        tracer = noopTracer,
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
    fun `produces output directly from sources when LLM returns output block`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenReturn(
            """Based on the internal sources, here is my analysis:

```output
## Market Analysis: AI in Healthcare

AI adoption in healthcare is growing at 25% annually. Key drivers include:
- Diagnostic imaging improvements
- Electronic health record optimization
- Drug discovery acceleration

Sources: Healthcare AI Report 2026
```"""
        )

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        assertTrue(succeeded.curatedText.contains("AI adoption in healthcare"))
        assertNotNull(succeeded.sourceIdsUsedJson)
        assertNotNull(succeeded.toolStatsJson)
        verify(aiAdapter, times(1)).complete(any(), any(), any(), any(), any())
        verifyNoInteractions(webSearchTool)
    }

    @Test
    fun `uses source lookup and tracks returned source ids`() {
        val lookedUpSourceId = UUID.randomUUID()

        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenReturn(
                """```tool
{"tool": "source_lookup", "args": {"query": "related AI regulation sources", "limit": 3}}
```"""
            )
            .thenReturn(
                """```output
Analysis using related internal sources.
```"""
            )

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

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        val sourceIds = objectMapper.readTree(succeeded.sourceIdsUsedJson)
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
    fun `uses web search when LLM requests it`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenReturn(
                """I need more data on recent healthcare AI trends.

```tool
{"tool": "web_search", "args": {"query": "healthcare AI adoption 2026 trends"}}
```"""
            )
            .thenReturn(
                """Based on the search results and internal sources:

```output
## Healthcare AI Market Analysis

The market is expanding rapidly with key developments in 2026.

Sources: Healthcare AI Report 2026, Web search results
```"""
            )

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

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        assertTrue(succeeded.curatedText.contains("Healthcare AI"))
        assertNotNull(succeeded.referencesUsedJson)
        verify(webSearchTool).search(eq("healthcare AI adoption 2026 trends"), eq(5))
    }

    @Test
    fun `uses web fetch when LLM requests it`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenReturn(
                """I want to read a specific article.

```tool
{"tool": "web_fetch", "args": {"url": "https://example.com/article"}}
```"""
            )
            .thenReturn(
                """```output
Analysis based on fetched content.
```"""
            )

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

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        verify(webFetchTool).fetch(eq("https://example.com/article"))
    }

    @Test
    fun `returns EmptyOutput when LLM produces empty output`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenReturn(
            """```output

```"""
        )

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.EmptyOutput)
    }

    @Test
    fun `returns Failed on retryable tool error`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenReturn(
            """```tool
{"tool": "web_search", "args": {"query": "test query"}}
```"""
        )

        whenever(webSearchTool.search(any(), any())).thenReturn(
            ToolResult.Error(ToolErrorCode.HTTP_429, "Rate limited")
        )

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("http_429", failed.errorCode)
        assertTrue(failed.retryable)
    }

    @Test
    fun `continues on non-retryable tool error`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenReturn(
                """```tool
{"tool": "web_fetch", "args": {"url": "http://internal.server"}}
```"""
            )
            .thenReturn(
                """```output
Analysis without the blocked URL.
```"""
            )

        whenever(webFetchTool.fetch(any())).thenReturn(
            ToolResult.Error(ToolErrorCode.SSRF_BLOCKED, "SSRF blocked")
        )

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
    }

    @Test
    fun `respects tool call budget and forces final output`() {
        val maxCalls = 3
        val limitedRunner = AiSubagentExecutionRunner(
            aiAdapter = aiAdapter,
            tracer = noopTracer,
            webSearchTool = webSearchTool,
            webFetchTool = webFetchTool,
            sourceLookupTool = sourceLookupTool,
            objectMapper = objectMapper,
            config = config.copy(maxToolCalls = maxCalls)
        )

        // LLM keeps requesting tools
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenReturn("""```tool
{"tool": "web_search", "args": {"query": "query 1"}}
```""")
            .thenReturn("""```tool
{"tool": "web_search", "args": {"query": "query 2"}}
```""")
            .thenReturn("""```tool
{"tool": "web_search", "args": {"query": "query 3"}}
```""")
            // Final forced output
            .thenReturn("""```output
Budget-exhausted analysis.
```""")

        whenever(webSearchTool.search(any(), any())).thenReturn(
            ToolResult.Success(
                WebSearchResponse(
                    results = listOf(WebSearchResult("Result", "https://example.com", "snippet")),
                    query = "query"
                )
            )
        )

        val result = limitedRunner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        assertTrue(succeeded.curatedText.contains("Budget-exhausted"))
        val toolStats = objectMapper.readTree(succeeded.toolStatsJson)
        assertTrue(toolStats.get("budgetExhausted").asBoolean())
    }

    @Test
    fun `handles LLM exception as retryable failure`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("Connection timeout"))

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("runner_error", failed.errorCode)
        assertTrue(failed.retryable)
    }

    @Test
    fun `handles provider unavailable exception as retryable failure`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("503 Service Unavailable"))

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("runner_error", failed.errorCode)
        assertTrue(failed.retryable)
    }

    @Test
    fun `handles generic runner exception as retryable failure`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("boom"))

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("runner_error", failed.errorCode)
        assertTrue(failed.retryable)
    }

    @Test
    fun `handles bad request runner exception as non-retryable failure`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("400 Bad Request"))

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("runner_error", failed.errorCode)
        assertFalse(failed.retryable)
    }

    @Test
    fun `handles unauthorized runner exception as non-retryable failure`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("401 Unauthorized"))

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("runner_error", failed.errorCode)
        assertFalse(failed.retryable)
    }

    @Test
    fun `handles LLM exception as non-retryable when not transient`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenThrow(IllegalArgumentException("Invalid prompt format"))

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertFalse(failed.retryable)
    }

    @Test
    fun `works without web tools when they are null`() {
        val noToolsRunner = AiSubagentExecutionRunner(
            aiAdapter = aiAdapter,
            tracer = noopTracer,
            webSearchTool = null,
            webFetchTool = null,
            sourceLookupTool = null,
            objectMapper = objectMapper,
            config = config
        )

        // LLM tries to use web search but it's not available, then produces output
        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenReturn("""```tool
{"tool": "web_search", "args": {"query": "test"}}
```""")
            .thenReturn("""```output
Output without web tools.
```""")

        val result = noToolsRunner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        verifyNoInteractions(webSearchTool)
    }

    @Test
    fun `graceful fallback when LLM does not use output block format`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenReturn(
            "This is my analysis of the healthcare market. AI adoption is growing rapidly."
        )

        val result = runner.execute(baseContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        assertTrue(succeeded.curatedText.contains("healthcare market"))
    }

    @Test
    fun `empty sources handled gracefully`() {
        val emptySourceContext = baseContext.copy(sources = emptyList())

        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenReturn(
            """```output
Analysis with no internal sources.
```"""
        )

        val result = runner.execute(emptySourceContext)

        assertTrue(result is SubagentExecutionResult.Succeeded)
    }

    @Test
    fun `web search tool span is nested under active subagent span and records retry metadata`() {
        val spanExporter = InMemorySpanExporter.create()
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        val tracer = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build().getTracer("test")
        val tracedRunner = AiSubagentExecutionRunner(
            aiAdapter = aiAdapter,
            tracer = tracer,
            webSearchTool = webSearchTool,
            webFetchTool = webFetchTool,
            sourceLookupTool = sourceLookupTool,
            objectMapper = objectMapper,
            config = config
        )
        val retryContext = baseContext.copy(attempt = 2, retryReason = "http_429")

        whenever(aiAdapter.complete(any(), any(), any(), any(), any()))
            .thenReturn(
                """```tool
{"tool": "web_search", "args": {"query": "healthcare AI adoption 2026 trends"}}
```"""
            )
            .thenReturn(
                """```output
Analysis after retry.
```"""
            )
        whenever(webSearchTool.search(any(), any())).thenReturn(
            ToolResult.Success(
                WebSearchResponse(
                    results = listOf(WebSearchResult("AI Health 2026", "https://news.example.com/ai-health", "New trends...")),
                    query = "healthcare AI adoption 2026 trends"
                )
            )
        )

        val parentSpan = tracer.spanBuilder("subagent.market_analyst").startSpan()
        parentSpan.makeCurrent().use {
            tracedRunner.execute(retryContext)
        }
        parentSpan.end()

        val spans = spanExporter.finishedSpanItems.associateBy { it.name }
        val toolSpan = spans.getValue("tool.web_search")
        val subagentSpan = spans.getValue("subagent.market_analyst")

        assertEquals(subagentSpan.spanContext.spanId, toolSpan.parentSpanContext.spanId)
        assertEquals(2L, toolSpan.attributes.get(AttributeKey.longKey("attempt.number")))
        assertEquals(true, toolSpan.attributes.get(AttributeKey.booleanKey("retry")))
        assertEquals("http_429", toolSpan.attributes.get(AttributeKey.stringKey("retry.reason")))
        assertEquals("healthcare AI adoption 2026 trends", toolSpan.attributes.get(AttributeKey.stringKey("tool.query")))
    }
}
