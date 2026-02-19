package com.briefy.api.config

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64

@Configuration
class OpenTelemetryConfig(
    private val properties: AiObservabilityProperties
) {
    private val logger = LoggerFactory.getLogger(OpenTelemetryConfig::class.java)
    private var tracerProvider: SdkTracerProvider? = null

    @Bean
    fun openTelemetry(): OpenTelemetry {
        if (!properties.enabled) {
            logger.info("[ai-observability] disabled")
            return OpenTelemetry.noop()
        }

        val endpoint = resolveEndpoint()
        val authorizationHeader = resolveAuthorizationHeader()
        if (endpoint.isBlank() || authorizationHeader.isBlank()) {
            logger.warn("[ai-observability] enabled but OTLP endpoint/header missing; using no-op telemetry")
            return OpenTelemetry.noop()
        }

        val spanExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint)
            .addHeader("Authorization", authorizationHeader)
            .build()
        val provider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .build()
        tracerProvider = provider

        logger.info("[ai-observability] OpenTelemetry exporter configured endpoint={}", endpoint)
        return OpenTelemetrySdk.builder()
            .setTracerProvider(provider)
            .buildAndRegisterGlobal()
    }

    @Bean
    fun aiTracer(openTelemetry: OpenTelemetry): Tracer = openTelemetry.getTracer("com.briefy.api.ai")

    @PreDestroy
    fun shutdown() {
        tracerProvider?.shutdown()
    }

    private fun resolveEndpoint(): String {
        val rawEndpoint = System.getenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT")
            ?.takeIf { it.isNotBlank() }
            ?: System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
                ?.takeIf { it.isNotBlank() }
            ?: "${properties.langfuse.baseUrl.trim().trimEnd('/')}/api/public/otel/v1/traces"

        return normalizeTraceEndpoint(rawEndpoint.trim())
    }

    private fun normalizeTraceEndpoint(endpoint: String): String {
        val normalized = endpoint.trimEnd('/')
        return when {
            normalized.endsWith("/v1/traces") -> normalized
            normalized.endsWith("/api/public/otel") -> "$normalized/v1/traces"
            else -> normalized
        }
    }

    private fun resolveAuthorizationHeader(): String {
        val explicitAuth = System.getenv("OTEL_EXPORTER_OTLP_AUTHORIZATION")
            ?.takeIf { it.isNotBlank() }
        if (explicitAuth != null) {
            return explicitAuth
        }

        val rawHeaders = System.getenv("OTEL_EXPORTER_OTLP_TRACES_HEADERS")
            ?.takeIf { it.isNotBlank() }
            ?: System.getenv("OTEL_EXPORTER_OTLP_HEADERS")
                ?.takeIf { it.isNotBlank() }
        if (rawHeaders != null) {
            val authorizationPart = rawHeaders.split(",")
                .map { it.trim() }
                .firstOrNull { it.startsWith("Authorization=", ignoreCase = true) }
            if (authorizationPart != null) {
                return authorizationPart.substringAfter("=", "").trim()
            }
        }

        val publicKey = properties.langfuse.publicKey.trim()
        val secretKey = properties.langfuse.secretKey.trim()
        if (publicKey.isBlank() || secretKey.isBlank()) {
            return ""
        }

        val auth = Base64.getEncoder().encodeToString("$publicKey:$secretKey".toByteArray())
        return "Basic $auth"
    }
}
