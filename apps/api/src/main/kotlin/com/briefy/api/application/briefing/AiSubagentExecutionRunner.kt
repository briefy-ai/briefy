package com.briefy.api.application.briefing

import com.briefy.api.application.briefing.tool.SourceLookupResponse
import com.briefy.api.application.briefing.tool.SourceLookupTool
import com.briefy.api.application.briefing.tool.ToolErrorCode
import com.briefy.api.application.briefing.tool.ToolResult
import com.briefy.api.application.briefing.tool.UntrustedContentWrapper
import com.briefy.api.application.briefing.tool.WebFetchTool
import com.briefy.api.application.briefing.tool.WebSearchTool
import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.ai.AiErrorCategory
import com.briefy.api.infrastructure.ai.RetryableToolExecutionException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.ToolCallback
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.springframework.ai.tool.function.FunctionToolCallback
import java.util.UUID
import java.util.function.Function

class AiSubagentExecutionRunner(
    private val aiAdapter: AiAdapter,
    private val webSearchTool: WebSearchTool?,
    private val webFetchTool: WebFetchTool?,
    private val sourceLookupTool: SourceLookupTool?,
    private val objectMapper: ObjectMapper,
    private val config: AiRunnerConfig
) : SubagentExecutionRunner {

    private val logger = LoggerFactory.getLogger(AiSubagentExecutionRunner::class.java)

    override fun execute(context: SubagentExecutionContext): SubagentExecutionResult {
        return try {
            executeWithNativeTools(context)
        } catch (e: Exception) {
            findRetryableToolFailure(e)?.let { failure ->
                return SubagentExecutionResult.Failed(
                    errorCode = failure.errorCode,
                    errorMessage = failure.message,
                    retryable = true,
                    retryAfterSeconds = failure.retryAfterSeconds
                )
            }

            logger.error(
                "[ai-runner] Unexpected error for persona={} subagentRun={}",
                context.personaKey,
                context.subagentRunId,
                e
            )
            val failure = classifyRunnerFailure(e)
            SubagentExecutionResult.Failed(
                errorCode = failure.errorCode,
                errorMessage = failure.errorMessage,
                retryable = failure.retryable
            )
        }
    }

    private fun executeWithNativeTools(context: SubagentExecutionContext): SubagentExecutionResult {
        val toolSession = ToolSession(context)
        val sourceEvidence = buildSourceEvidence(context.sources)
        val systemPrompt = buildSystemPrompt(context)
        val prompt = buildInitialUserPrompt(context, sourceEvidence)
        val toolCallbacks = buildToolCallbacks(context, toolSession)

        val llmResponse = if (toolCallbacks.isEmpty()) {
            aiAdapter.complete(
                provider = config.provider,
                model = config.model,
                prompt = prompt,
                systemPrompt = systemPrompt,
                useCase = "subagent_execution_${context.personaKey}"
            )
        } else {
            aiAdapter.completeWithTools(
                provider = config.provider,
                model = config.model,
                prompt = prompt,
                systemPrompt = systemPrompt,
                useCase = "subagent_execution_${context.personaKey}",
                toolCallbacks = toolCallbacks
            )
        }

        val curatedText = extractCuratedText(llmResponse)
        if (curatedText.isBlank()) {
            logger.warn(
                "[ai-runner] EmptyOutput persona={} subagentRun={} toolCalls={} response={}",
                context.personaKey,
                context.subagentRunId,
                toolSession.toolCallCount,
                llmResponse.take(500)
            )
            return SubagentExecutionResult.EmptyOutput
        }

        return toolSession.succeeded(curatedText)
    }

    private fun buildToolCallbacks(
        context: SubagentExecutionContext,
        toolSession: ToolSession
    ): List<ToolCallback> {
        val callbacks = mutableListOf<ToolCallback>()

        if (sourceLookupTool != null) {
            callbacks += FunctionToolCallback.builder(
                "source_lookup",
                Function<SourceLookupToolRequest, String> { request ->
                    toolSession.execute {
                        executeSourceLookup(context, request.query, request.sourceId, request.limit)
                    }
                }
            )
                .description(
                    "Search the user's internal source library by similarity. " +
                        "Provide either query text or a sourceId from the current briefing sources."
                )
                .inputType(SourceLookupToolRequest::class.java)
                .build()
        }

        if (webSearchTool != null) {
            callbacks += FunctionToolCallback.builder(
                "web_search",
                Function<WebSearchToolRequest, String> { request ->
                    toolSession.execute {
                        executeWebSearch(request.query, request.maxResults)
                    }
                }
            )
                .description("Search the public web for relevant information and candidate URLs.")
                .inputType(WebSearchToolRequest::class.java)
                .build()
        }

        if (webFetchTool != null) {
            callbacks += FunctionToolCallback.builder(
                "web_fetch",
                Function<WebFetchToolRequest, String> { request ->
                    toolSession.execute {
                        executeWebFetch(request.url)
                    }
                }
            )
                .description("Fetch and read a specific public web page. Use sparingly and only for promising URLs.")
                .inputType(WebFetchToolRequest::class.java)
                .build()
        }

        return callbacks
    }

    private fun buildSourceEvidence(sources: List<BriefingSourceInput>): String {
        if (sources.isEmpty()) return "No internal sources available."
        return buildString {
            appendLine("## Internal Sources")
            sources.forEachIndexed { index, source ->
                appendLine()
                appendLine("### Source ${index + 1}: ${source.title}")
                appendLine("Source ID: ${source.sourceId}")
                appendLine("URL: ${source.url}")
                val text = source.text.trim()
                if (text.isNotBlank()) {
                    val truncated = if (text.length > MAX_SOURCE_CHARS) {
                        text.take(MAX_SOURCE_CHARS) + "..."
                    } else {
                        text
                    }
                    appendLine(truncated)
                } else {
                    appendLine("(no text content)")
                }
            }
        }
    }

    private fun buildSystemPrompt(context: SubagentExecutionContext): String {
        val availableTools = buildAvailableTools()
        val rules = buildToolUsageRules()
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
        return """You are "${context.personaName}", an AI research persona working on a briefing.

Today's date is $today.

Your task is to investigate a specific angle and produce a curated analysis backed by evidence.

## Available Tools
$availableTools

Use native tool calling when you need more evidence. Do not print tool calls in your answer.

## Rules
1. Start by analyzing the provided internal sources carefully.
2. If the internal sources provide sufficient evidence for your task, produce your output directly.
$rules

## Output Format

When you have enough evidence, produce your final output in this exact format:
```output
<your curated analysis here, in markdown>
```

Do not include anything after the output block."""
    }

    private fun buildInitialUserPrompt(context: SubagentExecutionContext, sourceEvidence: String): String {
        val nextStep = buildInitialToolGuidance()
        return """## Your Assignment
${context.task}

## Evidence from Internal Sources
$sourceEvidence

Analyze the internal sources above. If they provide sufficient evidence for your task, produce your ```output``` block directly.$nextStep"""
    }

    private fun extractCuratedText(llmResponse: String): String {
        val outputBlockRegex = Regex("```output\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        val match = outputBlockRegex.find(llmResponse)
        return match?.groupValues?.get(1)?.trim() ?: llmResponse.trim()
    }

    private fun executeWebSearch(query: String?, maxResults: Int?): ToolCallResult {
        if (webSearchTool == null) {
            return ToolCallResult("web_search is not enabled on this server.")
        }

        if (query.isNullOrBlank()) {
            return ToolCallResult("Missing 'query' argument for web_search.")
        }

        val clampedMaxResults = (maxResults ?: 5).coerceIn(1, 10)
        return when (val result = webSearchTool.search(query, clampedMaxResults)) {
            is ToolResult.Success -> {
                val wrapped = UntrustedContentWrapper.wrapSearchResults(result.data.results, result.data.query)
                val references = result.data.results.map {
                    WebReference(it.url, it.title, it.snippet)
                }
                ToolCallResult(wrapped, references = references)
            }

            is ToolResult.Error -> toToolCallResult("web_search", result)
        }
    }

    private fun executeWebFetch(url: String?): ToolCallResult {
        if (webFetchTool == null) {
            return ToolCallResult("web_fetch is not enabled on this server.")
        }

        if (url.isNullOrBlank()) {
            return ToolCallResult("Missing 'url' argument for web_fetch.")
        }

        return when (val result = webFetchTool.fetch(url)) {
            is ToolResult.Success -> {
                val wrapped = UntrustedContentWrapper.wrap(result.data.content, result.data.url)
                val references = listOf(WebReference(result.data.url, result.data.title, null))
                ToolCallResult(wrapped, references = references)
            }

            is ToolResult.Error -> toToolCallResult("web_fetch", result)
        }
    }

    private fun executeSourceLookup(
        context: SubagentExecutionContext,
        query: String?,
        sourceIdRaw: String?,
        limit: Int?
    ): ToolCallResult {
        if (sourceLookupTool == null) {
            return ToolCallResult("source_lookup is not enabled on this server.")
        }

        val sourceId = sourceIdRaw?.trim()?.takeIf { it.isNotBlank() }?.let { rawSourceId ->
            runCatching { UUID.fromString(rawSourceId) }.getOrElse {
                return ToolCallResult("Invalid 'sourceId' argument for source_lookup.")
            }
        }

        val clampedLimit = (limit ?: 5).coerceIn(1, 10)
        return when (
            val result = sourceLookupTool.lookup(
                query = query?.trim()?.takeIf { it.isNotBlank() },
                sourceId = sourceId,
                limit = clampedLimit,
                userId = context.userId,
                excludeSourceIds = context.sources.map { it.sourceId }.toSet()
            )
        ) {
            is ToolResult.Success -> ToolCallResult(
                content = formatSourceLookupResults(result.data),
                sourceIdsUsed = result.data.results.map { it.sourceId }
            )

            is ToolResult.Error -> toToolCallResult("source_lookup", result)
        }
    }

    private fun toToolCallResult(toolName: String, error: ToolResult.Error): ToolCallResult {
        return if (error.code.retryable) {
            ToolCallResult(
                content = "$toolName failed: ${error.message}",
                error = ToolCallError(error.code.toRunnerErrorCode(), error.message)
            )
        } else {
            ToolCallResult("$toolName failed (non-retryable): ${error.message}")
        }
    }

    private fun classifyRunnerFailure(error: Exception): RunnerFailure {
        val rootMessage = rootCause(error).message?.takeIf { it.isNotBlank() }
        val errorMessage = (rootMessage ?: error.message ?: "Unknown error").take(500)
        val normalizedMessage = errorMessage.lowercase()
        val retryable = when {
            isExplicitNonRetryableRunnerError(error, normalizedMessage) -> false
            isRateLimitedRunnerError(normalizedMessage) -> true
            else -> when (AiErrorCategory.from(error)) {
                AiErrorCategory.TIMEOUT,
                AiErrorCategory.PROVIDER_UNAVAILABLE,
                AiErrorCategory.UNKNOWN -> true
                AiErrorCategory.VALIDATION -> false
            }
        }

        return RunnerFailure(
            errorCode = "runner_error",
            errorMessage = errorMessage,
            retryable = retryable
        )
    }

    private fun findRetryableToolFailure(error: Throwable): RetryableToolExecutionException? {
        var current: Throwable? = error
        while (current != null && current.cause !== current) {
            if (current is RetryableToolExecutionException) {
                return current
            }
            current = current.cause
        }
        return null
    }

    private fun isExplicitNonRetryableRunnerError(error: Exception, message: String): Boolean {
        if (error is IllegalArgumentException || rootCause(error) is IllegalArgumentException) {
            return true
        }

        return NON_RETRYABLE_RUNNER_MESSAGE_MARKERS.any { marker -> message.contains(marker) }
    }

    private fun isRateLimitedRunnerError(message: String): Boolean {
        return RATE_LIMIT_MESSAGE_MARKERS.any { marker -> message.contains(marker) }
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    data class AiRunnerConfig(
        val provider: String = "google_genai",
        val model: String = "gemini-2.5-flash"
    )

    private inner class ToolSession(context: SubagentExecutionContext) {
        private val sourceIdsUsed = mutableSetOf<UUID>().apply {
            addAll(context.sources.map { it.sourceId })
        }
        private val referencesUsed = mutableListOf<WebReference>()

        var toolCallCount = 0
            private set

        fun execute(block: () -> ToolCallResult): String {
            toolCallCount++
            val result = block()
            if (result.error != null) {
                throw RetryableToolExecutionException(
                    errorCode = result.error.code,
                    message = result.error.message,
                    retryAfterSeconds = result.error.retryAfterSeconds
                )
            }

            sourceIdsUsed.addAll(result.sourceIdsUsed)
            result.references?.let { referencesUsed.addAll(it) }
            return result.content
        }

        fun succeeded(curatedText: String): SubagentExecutionResult.Succeeded {
            return SubagentExecutionResult.Succeeded(
                curatedText = curatedText,
                sourceIdsUsedJson = objectMapper.writeValueAsString(sourceIdsUsed),
                referencesUsedJson = referencesUsed
                    .takeIf { it.isNotEmpty() }
                    ?.let(objectMapper::writeValueAsString),
                toolStatsJson = objectMapper.writeValueAsString(mapOf("toolCallCount" to toolCallCount))
            )
        }
    }

    private data class ToolCallResult(
        val content: String,
        val references: List<WebReference>? = null,
        val sourceIdsUsed: List<UUID> = emptyList(),
        val error: ToolCallError? = null
    )
    private data class ToolCallError(val code: String, val message: String, val retryAfterSeconds: Long? = null)
    private data class RunnerFailure(val errorCode: String, val errorMessage: String, val retryable: Boolean)
    data class WebReference(val url: String, val title: String?, val snippet: String?)

    private data class WebSearchToolRequest(
        val query: String? = null,
        val maxResults: Int? = null
    )

    private data class WebFetchToolRequest(
        val url: String? = null
    )

    private data class SourceLookupToolRequest(
        val query: String? = null,
        val sourceId: String? = null,
        val limit: Int? = null
    )

    companion object {
        internal fun subagentSpanName(personaName: String, personaKey: String): String {
            val displayName = personaName.trim().takeIf { it.isNotBlank() } ?: personaKey
            return "subagent.$displayName"
        }

        private const val MAX_SOURCE_CHARS = 4000

        private val RATE_LIMIT_MESSAGE_MARKERS = setOf(
            "429",
            "rate limit",
            "rate-limit",
            "too many requests",
            "quota exceeded"
        )
        private val NON_RETRYABLE_RUNNER_MESSAGE_MARKERS = setOf(
            "400",
            "401",
            "403",
            "bad request",
            "unauthorized",
            "forbidden",
            "invalid api key",
            "api key not valid",
            "authentication failed",
            "invalid prompt",
            "prompt must not be blank",
            "provider must not be blank",
            "model must not be blank",
            "unsupported ai provider",
            "chat model is not configured",
            "invalid persona",
            "persona config",
            "missing source data"
        )
    }

    private fun buildAvailableTools(): String {
        val tools = mutableListOf<String>()
        if (sourceLookupTool != null) {
            tools += """- `source_lookup`: Search the user's internal source library by similarity using `query` or `sourceId`."""
        }
        if (webSearchTool != null) {
            tools += """- `web_search`: Search the web for relevant information using `query` and optional `maxResults`."""
        }
        if (webFetchTool != null) {
            tools += """- `web_fetch`: Fetch a specific public web page using `url`."""
        }
        return if (tools.isEmpty()) "- No tools are enabled." else tools.joinToString("\n")
    }

    private fun buildToolUsageRules(): String {
        val rules = mutableListOf<String>()
        var ruleNumber = 3
        if (sourceLookupTool != null) {
            rules += "${ruleNumber++}. If you need additional internal context, use `source_lookup`."
            rules += "${ruleNumber++}. In source-based lookups, pass an explicit `sourceId` from the provided internal sources."
        }
        if (webSearchTool != null) {
            rules += "${ruleNumber++}. If you need external context, use `web_search` before `web_fetch`."
        }
        if (webFetchTool != null) {
            rules += "${ruleNumber++}. Use `web_fetch` selectively — only for the most promising URLs from search results."
        }
        rules += "${ruleNumber}. Always cite internal source titles and web URLs that you used."
        return rules.joinToString("\n")
    }

    private fun buildInitialToolGuidance(): String {
        val guidance = mutableListOf<String>()
        if (sourceLookupTool != null) {
            guidance += "If you need more internal context, use the `source_lookup` tool."
        }
        if (webSearchTool != null) {
            guidance += "If you need external context, use the `web_search` tool."
        }
        return if (guidance.isEmpty()) "" else " ${guidance.joinToString(" ")}"
    }

    private fun formatSourceLookupResults(response: SourceLookupResponse): String {
        if (response.results.isEmpty()) {
            return "No similar internal sources found."
        }

        return buildString {
            appendLine("## Similar Internal Sources")
            appendLine("Mode: ${response.mode}")
            response.query?.let { appendLine("Query: ${UntrustedContentWrapper.sanitizeMarkers(it)}") }
            response.sourceId?.let { appendLine("Anchor Source ID: $it") }
            response.results.forEachIndexed { index, result ->
                appendLine()
                appendLine("${index + 1}. ${UntrustedContentWrapper.sanitizeMarkers(result.title ?: result.url)}")
                appendLine("   sourceId: ${result.sourceId}")
                appendLine("   url: ${UntrustedContentWrapper.sanitizeMarkers(result.url)}")
                appendLine("   score: ${"%.4f".format(result.score)}")
                appendLine("   wordCount: ${result.wordCount}")
                result.excerpt?.let {
                    appendLine("   excerpt: ${UntrustedContentWrapper.sanitizeMarkers(it)}")
                }
            }
        }.trim()
    }
}
