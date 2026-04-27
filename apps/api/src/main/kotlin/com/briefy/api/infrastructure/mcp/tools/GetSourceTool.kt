package com.briefy.api.infrastructure.mcp.tools

import com.briefy.api.domain.knowledgegraph.briefing.BriefingSourceRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingStatus
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.mcp.CurrentMcpUser
import com.briefy.api.infrastructure.mcp.McpJson
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.function.Function

@Component
class GetSourceTool(
    private val sourceRepository: SourceRepository,
    private val briefingSourceRepository: BriefingSourceRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val mcpJson: McpJson,
) {
    data class Input(val id: String)

    data class Result(
        val id: UUID,
        val title: String?,
        val author: String?,
        val url: String,
        val platform: String,
        val sourceType: String,
        val fullText: String?,
        val wordCount: Int,
        val publishedDate: Instant?,
        val topics: List<String>,
        val briefingIds: List<UUID>,
    )

    fun toolCallback(): ToolCallback = FunctionToolCallback.builder(
        "get_source",
        Function<Input, String> { execute(it) }
    )
        .description("Fetch a single source by ID with its full extracted text, metadata, assigned topics, and the IDs of briefings that reference it.")
        .inputType(Input::class.java)
        .build()

    private fun execute(input: Input): String {
        val userId = CurrentMcpUser.userId()
        val sourceId = runCatching { UUID.fromString(input.id) }.getOrNull()
            ?: return mcpJson.stringify(mapOf("error" to "Invalid source id"))

        val source = sourceRepository.findByIdAndUserId(sourceId, userId)
            ?: return mcpJson.stringify(mapOf("error" to "Source not found"))

        if (source.status != SourceStatus.ACTIVE) {
            return mcpJson.stringify(mapOf("error" to "Source not available"))
        }

        val topics = topicLinkRepository.findActiveTopicsBySourceIds(userId, listOf(source.id))
            .map { it.topicName }

        val briefingIds = briefingSourceRepository
            .findBriefingIdsByUserAndSourceAndStatus(userId, source.id, BriefingStatus.READY)
            .map { it.briefingId }
            .distinct()

        val result = Result(
            id = source.id,
            title = source.metadata?.title,
            author = source.metadata?.author,
            url = source.url.raw,
            platform = source.url.platform,
            sourceType = source.sourceType.name,
            fullText = source.content?.text,
            wordCount = source.content?.wordCount ?: 0,
            publishedDate = source.metadata?.publishedDate,
            topics = topics,
            briefingIds = briefingIds,
        )
        return mcpJson.stringify(result)
    }
}
