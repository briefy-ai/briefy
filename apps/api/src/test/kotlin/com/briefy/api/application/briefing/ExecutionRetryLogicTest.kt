package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.Briefing
import com.briefy.api.domain.knowledgegraph.briefing.BriefingEnrichmentIntent
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
import com.briefy.api.infrastructure.ai.AiPayloadSanitizer
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.UUID

class ExecutionRetryLogicTest {

    @Test
    fun `orchestrator accepts config properties for timeout and retry`() {
        val config = ExecutionConfigProperties(
            globalTimeoutSeconds = 300L,
            subagentTimeoutSeconds = 120L,
            maxAttempts = 5,
            retry = ExecutionConfigProperties.RetryConfig(
                transientDelayFirstSeconds = 2L,
                transientDelaySecondSeconds = 4L,
                http429FallbackFirstSeconds = 5L,
                http429FallbackSecondSeconds = 10L
            )
        )
        val orchestrator = BriefingExecutionOrchestratorService(
            briefingRunRepository = mock(),
            subagentRunRepository = mock(),
            synthesisRunRepository = mock(),
            briefingPlanStepRepository = mock(),
            executionStateTransitionService = mock(),
            subagentExecutionRunner = mock(),
            synthesisExecutionRunner = mock(),
            executionFingerprintService = mock(),
            executionConfig = config,
            sourceRepository = mock(),
            idGenerator = mock(),
            objectMapper = ObjectMapper(),
            tracer = OpenTelemetry.noop().getTracer("test"),
            sanitizer = AiPayloadSanitizer()
        )
        assertNotNull(orchestrator)
        assertEquals(300L, config.globalTimeoutSeconds)
        assertEquals(5, config.maxAttempts)
        assertEquals(10L, config.retry.http429FallbackSecondSeconds)
    }

    @Test
    fun `non-retryable failure result carries retryable=false`() {
        val result = SubagentExecutionResult.Failed(
            errorCode = "deterministic_failure",
            errorMessage = "Hard failure",
            retryable = false
        )
        assertFalse(result.retryable)
        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `retryable failure with retryAfterSeconds carries structured value`() {
        val result = SubagentExecutionResult.Failed(
            errorCode = "http_429",
            errorMessage = "Rate limited",
            retryable = true,
            retryAfterSeconds = 10L
        )
        assertTrue(result.retryable)
        assertEquals(10L, result.retryAfterSeconds)
    }

    @Test
    fun `retryable failure without retryAfterSeconds defaults to null`() {
        val result = SubagentExecutionResult.Failed(
            errorCode = "timeout",
            errorMessage = "Connection timed out",
            retryable = true
        )
        assertTrue(result.retryable)
        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `config defaults match legacy hardcoded values`() {
        val config = ExecutionConfigProperties()
        assertEquals(180L, config.globalTimeoutSeconds)
        assertEquals(90L, config.subagentTimeoutSeconds)
        assertEquals(3, config.maxAttempts)
        assertEquals(ExecutionConfigProperties.SynthesisType.AI, config.synthesis)
        assertEquals(1L, config.retry.transientDelayFirstSeconds)
        assertEquals(2L, config.retry.transientDelaySecondSeconds)
        assertEquals(2L, config.retry.http429FallbackFirstSeconds)
        assertEquals(4L, config.retry.http429FallbackSecondSeconds)
    }

    @Test
    fun `subagent span name prefers persona display name`() {
        assertEquals("subagent.The Economist", AiSubagentExecutionRunner.subagentSpanName("The Economist", "the_economist"))
        assertEquals("subagent.the_economist", AiSubagentExecutionRunner.subagentSpanName("", "the_economist"))
    }

    @Test
    fun `root briefing generation span records input and output payloads`() {
        val spanExporter = InMemorySpanExporter.create()
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        val tracer = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build().getTracer("test")
        val briefingId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val briefing = Briefing.create(
            id = briefingId,
            userId = userId,
            enrichmentIntent = BriefingEnrichmentIntent.DEEP_DIVE
        )
        val source = Source(
            id = UUID.randomUUID(),
            url = Url.from("https://example.com/source"),
            status = SourceStatus.ACTIVE,
            content = Content.from("Economic analysis"),
            metadata = Metadata.from(
                title = "Economic Notes",
                author = "Author",
                publishedDate = Instant.now(),
                platform = "web",
                wordCount = 123,
                aiFormatted = true,
                extractionProvider = "jsoup"
            ),
            userId = userId
        )
        val orchestrator = BriefingExecutionOrchestratorService(
            briefingRunRepository = mock(),
            subagentRunRepository = mock(),
            synthesisRunRepository = mock(),
            briefingPlanStepRepository = mock(),
            executionStateTransitionService = mock(),
            subagentExecutionRunner = mock(),
            synthesisExecutionRunner = mock(),
            executionFingerprintService = mock(),
            executionConfig = ExecutionConfigProperties(),
            sourceRepository = mock(),
            idGenerator = mock(),
            objectMapper = ObjectMapper(),
            tracer = tracer,
            sanitizer = AiPayloadSanitizer()
        )

        orchestrator.executeApprovedBriefing(briefing, listOf(source), emptyList())

        val span = spanExporter.finishedSpanItems.single()
        assertEquals("briefing.generation", span.name)
        assertTrue(span.attributes.get(AttributeKey.stringKey("input.value"))!!.contains(briefingId.toString()))
        assertTrue(span.attributes.get(AttributeKey.stringKey("input.value"))!!.contains("Economic Notes"))
        assertTrue(
            span.attributes.get(AttributeKey.stringKey("output.value"))!!
                .contains("Briefing plan must include at least one step")
        )
    }
}
