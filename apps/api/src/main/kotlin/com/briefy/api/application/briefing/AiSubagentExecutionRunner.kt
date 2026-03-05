package com.briefy.api.application.briefing

import com.briefy.api.application.briefing.tool.*
import com.briefy.api.infrastructure.ai.AiAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class AiSubagentExecutionRunner(
    private val aiAdapter: AiAdapter,
    private val webSearchTool: WebSearchTool?,
    private val webFetchTool: WebFetchTool?,
    private val objectMapper: ObjectMapper,
    private val config: AiRunnerConfig
) : SubagentExecutionRunner {

    private val logger = LoggerFactory.getLogger(AiSubagentExecutionRunner::class.java)

    override fun execute(context: SubagentExecutionContext): SubagentExecutionResult {
        return try {
            executeToolLoop(context)
        } catch (e: Exception) {
            logger.error("[ai-runner] Unexpected error for persona={} subagentRun={}", context.personaKey, context.subagentRunId, e)
            SubagentExecutionResult.Failed(
                errorCode = "runner_error",
                errorMessage = e.message?.take(500) ?: "Unknown error",
                retryable = isTransientException(e)
            )
        }
    }

    private fun executeToolLoop(context: SubagentExecutionContext): SubagentExecutionResult {
        val sourceIdsUsed = mutableSetOf<UUID>()
        val referencesUsed = mutableListOf<WebReference>()
        val toolCalls = mutableListOf<ToolCallRecord>()

        // Phase 1: Source lookup — build evidence from internal sources
        val sourceEvidence = buildSourceEvidence(context.sources)
        sourceIdsUsed.addAll(context.sources.map { it.sourceId })

        // Phase 2: LLM tool loop — the LLM decides whether to search/fetch
        val collectedEvidence = StringBuilder()
        collectedEvidence.append(sourceEvidence)

        var toolCallCount = 0
        var phase = "initial_analysis"

        // First LLM call: analyze sources and decide if web search is needed
        var llmMessages = mutableListOf<LlmMessage>()
        llmMessages.add(LlmMessage("system", buildSystemPrompt(context)))
        llmMessages.add(LlmMessage("user", buildInitialUserPrompt(context, sourceEvidence)))

        while (toolCallCount < config.maxToolCalls) {
            val llmResponse = callLlm(context, llmMessages)

            val toolRequest = parseToolRequest(llmResponse)
            if (toolRequest == null) {
                // LLM produced final output — extract curated text
                val curatedText = extractCuratedText(llmResponse)
                if (curatedText.isBlank()) {
                    return SubagentExecutionResult.EmptyOutput
                }
                return SubagentExecutionResult.Succeeded(
                    curatedText = curatedText,
                    sourceIdsUsedJson = objectMapper.writeValueAsString(sourceIdsUsed),
                    referencesUsedJson = if (referencesUsed.isNotEmpty())
                        objectMapper.writeValueAsString(referencesUsed)
                    else null,
                    toolStatsJson = objectMapper.writeValueAsString(
                        mapOf(
                            "runner" to "ai_subagent",
                            "toolCallCount" to toolCallCount,
                            "sourceCount" to sourceIdsUsed.size,
                            "webReferencesCount" to referencesUsed.size,
                            "tools" to toolCalls.map { mapOf("tool" to it.tool, "durationMs" to it.durationMs, "success" to it.success) }
                        )
                    )
                )
            }

            // Execute the requested tool
            toolCallCount++
            val toolStart = System.currentTimeMillis()
            val toolResult = executeTool(toolRequest, context)
            val toolDurationMs = System.currentTimeMillis() - toolStart
            val toolSuccess = toolResult.error == null

            toolCalls.add(ToolCallRecord(toolRequest.tool, toolDurationMs, toolSuccess))

            if (toolResult.error != null && isToolErrorRetryable(toolResult.error)) {
                return SubagentExecutionResult.Failed(
                    errorCode = toolResult.error.code,
                    errorMessage = toolResult.error.message,
                    retryable = true,
                    retryAfterSeconds = toolResult.error.retryAfterSeconds
                )
            }

            // Track references from web tools
            toolResult.references?.let { referencesUsed.addAll(it) }

            // Add tool result to conversation and continue loop
            llmMessages.add(LlmMessage("assistant", llmResponse))
            llmMessages.add(LlmMessage("user", buildToolResultPrompt(toolRequest.tool, toolResult.content)))

            phase = "tool_loop_${toolCallCount}"
        }

        // Budget exhausted — ask LLM for final output with what we have
        llmMessages.add(LlmMessage("user", BUDGET_EXHAUSTED_PROMPT))
        val finalResponse = callLlm(context, llmMessages)
        val curatedText = extractCuratedText(finalResponse)
        if (curatedText.isBlank()) {
            return SubagentExecutionResult.EmptyOutput
        }

        return SubagentExecutionResult.Succeeded(
            curatedText = curatedText,
            sourceIdsUsedJson = objectMapper.writeValueAsString(sourceIdsUsed),
            referencesUsedJson = if (referencesUsed.isNotEmpty())
                objectMapper.writeValueAsString(referencesUsed)
            else null,
            toolStatsJson = objectMapper.writeValueAsString(
                mapOf(
                    "runner" to "ai_subagent",
                    "toolCallCount" to toolCallCount,
                    "sourceCount" to sourceIdsUsed.size,
                    "webReferencesCount" to referencesUsed.size,
                    "budgetExhausted" to true,
                    "tools" to toolCalls.map { mapOf("tool" to it.tool, "durationMs" to it.durationMs, "success" to it.success) }
                )
            )
        )
    }

    private fun buildSourceEvidence(sources: List<BriefingSourceInput>): String {
        if (sources.isEmpty()) return "No internal sources available."
        return buildString {
            appendLine("## Internal Sources")
            sources.forEachIndexed { i, source ->
                appendLine()
                appendLine("### Source ${i + 1}: ${source.title}")
                appendLine("URL: ${source.url}")
                val text = source.text.trim()
                if (text.isNotBlank()) {
                    val truncated = if (text.length > MAX_SOURCE_CHARS) text.take(MAX_SOURCE_CHARS) + "..." else text
                    appendLine(truncated)
                } else {
                    appendLine("(no text content)")
                }
            }
        }
    }

    private fun buildSystemPrompt(context: SubagentExecutionContext): String {
        return """You are "${context.personaName}", an AI research persona working on a briefing.

Your task is to investigate a specific angle and produce a curated analysis backed by evidence.

## Available Tools

You can request tool calls by responding with a JSON block in this exact format:
```tool
{"tool": "<tool_name>", "args": {<args>}}
```

Available tools:
- `web_search`: Search the web for information. Args: {"query": "search query", "maxResults": 5}
- `web_fetch`: Fetch and read a specific web page. Args: {"url": "https://..."}

## Rules
1. Start by analyzing the provided internal sources carefully.
2. If the internal sources provide sufficient evidence for your task, produce your output directly.
3. If you need additional information, use `web_search` to discover relevant sources.
4. Use `web_fetch` selectively — only for the most promising URLs from search results.
5. Do NOT fetch more than 3 URLs per investigation.
6. Always cite your sources — reference internal source titles and web URLs used.

## Output Format

When you have enough evidence, produce your final output in this exact format:
```output
<your curated analysis here, in markdown>
```

Do NOT include the tool call format in your final output. Either request a tool OR produce output, not both."""
    }

    private fun buildInitialUserPrompt(context: SubagentExecutionContext, sourceEvidence: String): String {
        return """## Your Assignment
${context.task}

## Evidence from Internal Sources
$sourceEvidence

Analyze the internal sources above. If they provide sufficient evidence for your task, produce your ```output``` block directly. If you need more information from the web, request a `web_search` tool call."""
    }

    private fun buildToolResultPrompt(tool: String, content: String): String {
        return """## Tool Result: $tool
$content

Continue your investigation. If you have enough evidence, produce your ```output``` block. Otherwise, request another tool call."""
    }

    private fun callLlm(context: SubagentExecutionContext, messages: List<LlmMessage>): String {
        val prompt = messages.filter { it.role != "system" }
            .joinToString("\n\n---\n\n") { it.content }
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content

        return aiAdapter.complete(
            provider = config.provider,
            model = config.model,
            prompt = prompt,
            systemPrompt = systemPrompt,
            useCase = "subagent_execution_${context.personaKey}"
        )
    }

    private fun parseToolRequest(llmResponse: String): ToolRequest? {
        val toolBlockRegex = Regex("```tool\\s*\\n(\\{.*?})\\s*\\n```", RegexOption.DOT_MATCHES_ALL)
        val match = toolBlockRegex.find(llmResponse) ?: return null

        return try {
            val json = objectMapper.readTree(match.groupValues[1])
            val tool = json.get("tool")?.asText() ?: return null
            val args = json.get("args") ?: objectMapper.createObjectNode()
            ToolRequest(tool, args)
        } catch (e: Exception) {
            logger.warn("[ai-runner] Failed to parse tool request from LLM response", e)
            null
        }
    }

    private fun extractCuratedText(llmResponse: String): String {
        val outputBlockRegex = Regex("```output\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        val match = outputBlockRegex.find(llmResponse)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        // If no output block but no tool request either, treat the whole response as output
        // (graceful fallback for LLMs that don't follow format perfectly)
        val toolBlockRegex = Regex("```tool\\s*\\n", RegexOption.DOT_MATCHES_ALL)
        if (!toolBlockRegex.containsMatchIn(llmResponse)) {
            return llmResponse.trim()
        }
        return ""
    }

    private fun executeTool(request: ToolRequest, context: SubagentExecutionContext): ToolCallResult {
        return when (request.tool) {
            "web_search" -> executeWebSearch(request)
            "web_fetch" -> executeWebFetch(request)
            else -> {
                logger.warn("[ai-runner] Unknown tool requested: {}", request.tool)
                ToolCallResult("Tool '${request.tool}' is not available. Use web_search or web_fetch.")
            }
        }
    }

    private fun executeWebSearch(request: ToolRequest): ToolCallResult {
        if (webSearchTool == null) {
            return ToolCallResult("web_search is not enabled on this server.")
        }
        val query = request.args.get("query")?.asText() ?: return ToolCallResult("Missing 'query' argument for web_search.")
        val maxResults = request.args.get("maxResults")?.asInt() ?: 5

        return when (val result = webSearchTool.search(query, maxResults.coerceIn(1, 10))) {
            is ToolResult.Success -> {
                val wrapped = UntrustedContentWrapper.wrapSearchResults(result.data.results, result.data.query)
                val references = result.data.results.map { WebReference(it.url, it.title, it.snippet) }
                ToolCallResult(wrapped, references = references)
            }
            is ToolResult.Error -> {
                if (result.code.retryable) {
                    ToolCallResult(
                        "web_search failed: ${result.message}",
                        error = ToolCallError(result.code.toRunnerErrorCode(), result.message)
                    )
                } else {
                    ToolCallResult("web_search failed (non-retryable): ${result.message}")
                }
            }
        }
    }

    private fun executeWebFetch(request: ToolRequest): ToolCallResult {
        if (webFetchTool == null) {
            return ToolCallResult("web_fetch is not enabled on this server.")
        }
        val url = request.args.get("url")?.asText() ?: return ToolCallResult("Missing 'url' argument for web_fetch.")

        return when (val result = webFetchTool.fetch(url)) {
            is ToolResult.Success -> {
                val wrapped = UntrustedContentWrapper.wrap(result.data.content, result.data.url)
                val reference = WebReference(result.data.url, result.data.title, null)
                ToolCallResult(wrapped, references = listOf(reference))
            }
            is ToolResult.Error -> {
                if (result.code.retryable) {
                    ToolCallResult(
                        "web_fetch failed: ${result.message}",
                        error = ToolCallError(result.code.toRunnerErrorCode(), result.message)
                    )
                } else {
                    ToolCallResult("web_fetch failed (non-retryable): ${result.message}")
                }
            }
        }
    }

    private fun isToolErrorRetryable(error: ToolCallError): Boolean {
        return error.code in setOf("timeout", "http_429", "http_5xx", "network_error")
    }

    private fun isTransientException(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("timeout") || message.contains("connection") || message.contains("429")
    }

    data class AiRunnerConfig(
        val provider: String = "google_genai",
        val model: String = "gemini-2.5-flash",
        val maxToolCalls: Int = 8
    )

    private data class LlmMessage(val role: String, val content: String)
    private data class ToolRequest(val tool: String, val args: com.fasterxml.jackson.databind.JsonNode)
    private data class ToolCallRecord(val tool: String, val durationMs: Long, val success: Boolean)
    private data class ToolCallResult(
        val content: String,
        val references: List<WebReference>? = null,
        val error: ToolCallError? = null
    )
    private data class ToolCallError(val code: String, val message: String, val retryAfterSeconds: Long? = null)
    data class WebReference(val url: String, val title: String?, val snippet: String?)

    companion object {
        private const val MAX_SOURCE_CHARS = 4000
        private const val BUDGET_EXHAUSTED_PROMPT = "You have used all available tool calls. Based on the evidence collected so far, produce your final ```output``` block now."
    }
}
