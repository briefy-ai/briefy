package com.briefy.api.infrastructure.ai

import com.briefy.api.config.AiObservabilityProperties
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiCallObserverTest {
    @Test
    fun `records success span attributes`() {
        val spanExporter = InMemorySpanExporter.create()
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        val tracer = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build().getTracer("test")

        val observer = AiCallObserver(tracer, enabledProperties(), AiPayloadSanitizer())
        val longPrompt = "prefix ".repeat(50) + "sk-lf-abcdefghijklmnop"

        val result = observer.observeCompletion(
            provider = "google_genai",
            model = "gemini-2.5-flash-lite",
            useCase = "source_formatting",
            prompt = longPrompt,
            systemPrompt = "system"
        ) { "output" }

        assertEquals("output", result)
        val span = spanExporter.finishedSpanItems.single()
        assertEquals("ai.completion", span.name)
        assertEquals("source_formatting", span.attributes.get(AttributeKey.stringKey("ai.use_case")))
        assertEquals(true, span.attributes.get(AttributeKey.booleanKey("ai.success")))
        assertEquals("gemini-2.5-flash-lite", span.attributes.get(AttributeKey.stringKey("gen_ai.request.model")))
        assertEquals("google_genai", span.attributes.get(AttributeKey.stringKey("gen_ai.system")))
        assertEquals("output", span.attributes.get(AttributeKey.stringKey("ai.output.preview")))
        assertEquals("output", span.attributes.get(AttributeKey.stringKey("output.value")))
        val promptPreview = span.attributes.get(AttributeKey.stringKey("ai.prompt.preview")).orEmpty()
        assertTrue(promptPreview.contains("[REDACTED]"))
        assertTrue(!promptPreview.contains("[truncated]"))
        assertEquals(promptPreview, span.attributes.get(AttributeKey.stringKey("input.value")))
        assertEquals(null, span.attributes.get(AttributeKey.stringKey("ai.error.category")))
    }

    @Test
    fun `records normalized error category on failure`() {
        val spanExporter = InMemorySpanExporter.create()
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        val tracer = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build().getTracer("test")
        val observer = AiCallObserver(tracer, enabledProperties(), AiPayloadSanitizer())

        assertThrows(IllegalArgumentException::class.java) {
            observer.observeCompletion(
                provider = "zhipuai",
                model = "glm-4.7",
                useCase = "topic_extraction",
                prompt = "prompt",
                systemPrompt = null
            ) {
                throw IllegalArgumentException("invalid prompt")
            }
        }

        val span = spanExporter.finishedSpanItems.single()
        assertEquals("validation", span.attributes.get(AttributeKey.stringKey("ai.error.category")))
        assertEquals(false, span.attributes.get(AttributeKey.booleanKey("ai.success")))
    }

    @Test
    fun `does not mirror payload or error fields to parent span`() {
        val spanExporter = InMemorySpanExporter.create()
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        val tracer = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build().getTracer("test")
        val observer = AiCallObserver(tracer, enabledProperties(), AiPayloadSanitizer())

        val parentSpan = tracer.spanBuilder("briefing.generation").startSpan()
        parentSpan.makeCurrent().use {
            observer.observeCompletion(
                provider = "google_genai",
                model = "gemini-2.5-flash-lite",
                useCase = "source_formatting",
                prompt = "prompt",
                systemPrompt = null
            ) { "output" }
        }
        parentSpan.end()

        val spans = spanExporter.finishedSpanItems.associateBy { it.name }
        val parent = spans.getValue("briefing.generation")
        val child = spans.getValue("ai.completion")

        assertEquals(parent.spanContext.spanId, child.parentSpanContext.spanId)
        assertNull(parent.attributes.get(AttributeKey.stringKey("input.value")))
        assertNull(parent.attributes.get(AttributeKey.stringKey("output.value")))
        assertNull(parent.attributes.get(AttributeKey.stringKey("ai.error.category")))
        assertNull(parent.attributes.get(AttributeKey.longKey("ai.latency_ms")))
    }

    private fun enabledProperties(): AiObservabilityProperties {
        return AiObservabilityProperties().apply {
            enabled = true
        }
    }
}
