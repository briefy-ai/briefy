package com.briefy.api.infrastructure.ai

import com.briefy.api.config.AiObservabilityProperties
import io.opentelemetry.api.common.AttributeKey
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
            }

            captureInput(span, prompt, systemPrompt)

            try {
                val result = operation()
                span.setStatus(StatusCode.OK)
                span.setAttribute("ai.success", true)
                span.setAttribute("ai.output.length", result.length.toLong())
                captureOutput(span, result)
                result
            } catch (error: Throwable) {
                val errorCategory = AiErrorCategory.from(error)
                span.recordException(error)
                span.setStatus(StatusCode.ERROR, errorCategory.name.lowercase())
                span.setAttribute("ai.error.category", errorCategory.name.lowercase())
                throw error
            } finally {
                val latencyMillis = (System.nanoTime() - startedAt) / 1_000_000
                span.setAttribute("ai.latency_ms", latencyMillis)
                span.end()
            }
        }
    }

    private fun captureInput(span: io.opentelemetry.api.trace.Span, prompt: String, systemPrompt: String?) {
        span.setAttribute("ai.prompt.length", prompt.length.toLong())
        if (!systemPrompt.isNullOrBlank()) {
            span.setAttribute("ai.system_prompt.length", systemPrompt.length.toLong())
        }

        val promptPreview = sanitizer.sanitize(prompt)
        span.setAttribute("ai.prompt.preview", promptPreview)
        span.setAttribute("input.value", promptPreview)
        if (!systemPrompt.isNullOrBlank()) {
            val systemPromptPreview = sanitizer.sanitize(systemPrompt)
            span.setAttribute("ai.system_prompt.preview", systemPromptPreview)
        }
    }

    private fun captureOutput(span: io.opentelemetry.api.trace.Span, output: String) {
        val outputPreview = sanitizer.sanitize(output)
        span.setAttribute("ai.output.preview", outputPreview)
        span.setAttribute("output.value", outputPreview)
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
}
