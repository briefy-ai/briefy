package com.briefy.api.infrastructure.ai

import com.briefy.api.config.AiObservabilityProperties
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.slf4j.MDC
import org.springframework.stereotype.Component

@Component
class AiCallObserver(
    private val tracer: Tracer,
    private val properties: AiObservabilityProperties,
    private val sanitizer: AiPayloadSanitizer
) {
    fun observeCompletion(
        provider: String,
        model: String,
        useCase: String?,
        prompt: String,
        systemPrompt: String?,
        operation: () -> String
    ): String {
        if (!properties.enabled) {
            return operation()
        }

        val span = tracer.spanBuilder("ai.completion")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()
        val parentSpan = Span.current()
        val startedAt = System.nanoTime()
        val tags = buildTags(useCase, provider)
        val userId = resolveUserId()

        return span.makeCurrent().use {
            span.setAttribute("ai.provider", provider)
            span.setAttribute("ai.model", model)
            span.setAttribute("ai.use_case", useCase.orEmpty())
            span.setAttribute("ai.success", false)
            span.setAttribute("gen_ai.request.model", model)
            span.setAttribute("gen_ai.system", provider)
            span.setAttribute(AttributeKey.stringArrayKey("langfuse.tags"), tags)
            if (userId != null) {
                span.setAttribute("langfuse.user.id", userId)
                copyToParent(parentSpan, "langfuse.user.id", userId)
            }
            copyToParent(parentSpan, AttributeKey.stringArrayKey("langfuse.tags"), tags)

            captureInput(span, parentSpan, prompt, systemPrompt)

            try {
                val result = operation()
                span.setStatus(StatusCode.OK)
                span.setAttribute("ai.success", true)
                span.setAttribute("ai.output.length", result.length.toLong())
                captureOutput(span, parentSpan, result)
                result
            } catch (error: Throwable) {
                val errorCategory = AiErrorCategory.from(error)
                span.recordException(error)
                span.setStatus(StatusCode.ERROR, errorCategory.name.lowercase())
                span.setAttribute("ai.error.category", errorCategory.name.lowercase())
                copyToParent(parentSpan, "ai.error.category", errorCategory.name.lowercase())
                throw error
            } finally {
                val latencyMillis = (System.nanoTime() - startedAt) / 1_000_000
                span.setAttribute("ai.latency_ms", latencyMillis)
                copyToParent(parentSpan, "ai.latency_ms", latencyMillis)
                span.end()
            }
        }
    }

    private fun captureInput(span: Span, parentSpan: Span, prompt: String, systemPrompt: String?) {
        span.setAttribute("ai.prompt.length", prompt.length.toLong())
        if (!systemPrompt.isNullOrBlank()) {
            span.setAttribute("ai.system_prompt.length", systemPrompt.length.toLong())
        }

        val payloadLimit = if (properties.captureFullPayloads) Int.MAX_VALUE else properties.maxPayloadChars
        val promptPreview = sanitizer.sanitize(prompt, payloadLimit)
        span.setAttribute("ai.prompt.preview", promptPreview)
        span.setAttribute("input.value", promptPreview)
        copyToParent(parentSpan, "input.value", promptPreview)
        if (!systemPrompt.isNullOrBlank()) {
            val systemPromptPreview = sanitizer.sanitize(systemPrompt, payloadLimit)
            span.setAttribute("ai.system_prompt.preview", systemPromptPreview)
        }
    }

    private fun captureOutput(span: Span, parentSpan: Span, output: String) {
        val payloadLimit = if (properties.captureFullPayloads) Int.MAX_VALUE else properties.maxPayloadChars
        val outputPreview = sanitizer.sanitize(output, payloadLimit)
        span.setAttribute("ai.output.preview", outputPreview)
        span.setAttribute("output.value", outputPreview)
        copyToParent(parentSpan, "output.value", outputPreview)
    }

    private fun resolveUserId(): String? {
        val userId = MDC.get("userId")?.trim()
        return userId?.takeIf { it.isNotBlank() && it != "anonymous" }
    }

    private fun buildTags(useCase: String?, provider: String): List<String> {
        val tags = mutableListOf("component:ai", "provider:$provider")
        if (!useCase.isNullOrBlank()) {
            tags.add("use_case:$useCase")
        }
        return tags
    }

    private fun copyToParent(parentSpan: Span, key: String, value: String) {
        if (!parentSpan.spanContext.isValid) return
        parentSpan.setAttribute(key, value)
    }

    private fun copyToParent(parentSpan: Span, key: String, value: Long) {
        if (!parentSpan.spanContext.isValid) return
        parentSpan.setAttribute(key, value)
    }

    private fun copyToParent(parentSpan: Span, key: AttributeKey<List<String>>, value: List<String>) {
        if (!parentSpan.spanContext.isValid) return
        parentSpan.setAttribute(key, value)
    }
}
