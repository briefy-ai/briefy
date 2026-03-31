package com.briefy.api.infrastructure.ai

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TracingSupportTest {
    @Test
    fun `ends span when configure throws`() {
        val spanExporter = InMemorySpanExporter.create()
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        val tracer = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build().getTracer("test")

        val error = assertThrows(IllegalStateException::class.java) {
            tracer.withSpan(
                name = "test.span",
                configure = {
                    it.setAttribute("test.phase", "configure")
                    throw IllegalStateException("configure failed")
                }
            ) {
                "unreachable"
            }
        }

        assertEquals("configure failed", error.message)
        val span = spanExporter.finishedSpanItems.single()
        assertEquals("test.span", span.name)
        assertEquals("configure", span.attributes.get(AttributeKey.stringKey("test.phase")))
        assertEquals(StatusCode.ERROR, span.status.statusCode)
        assertEquals("configure failed", span.status.description)
    }
}
