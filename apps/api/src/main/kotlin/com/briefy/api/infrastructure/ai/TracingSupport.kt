package com.briefy.api.infrastructure.ai

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

inline fun <T> Tracer.withSpan(
    name: String,
    noParent: Boolean = false,
    crossinline configure: (Span) -> Unit = {},
    block: (Span) -> T
): T {
    val builder = spanBuilder(name)
    if (noParent) {
        builder.setNoParent()
    }

    val span = builder.startSpan()
    return span.makeCurrent().use {
        try {
            configure(span)
            block(span)
        } catch (error: Throwable) {
            span.recordException(error)
            span.setStatus(StatusCode.ERROR, error.message ?: error.javaClass.simpleName)
            throw error
        } finally {
            span.end()
        }
    }
}

fun Span.setAttributeIfNotBlank(key: String, value: String?) {
    if (!value.isNullOrBlank()) {
        setAttribute(key, value)
    }
}

fun Span.setAttributeIfNotNull(key: String, value: Long?) {
    if (value != null) {
        setAttribute(key, value)
    }
}
