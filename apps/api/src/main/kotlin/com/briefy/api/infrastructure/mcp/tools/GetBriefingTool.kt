package com.briefy.api.infrastructure.mcp.tools

import com.briefy.api.domain.knowledgegraph.briefing.BriefingReferenceRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingReferenceStatus
import com.briefy.api.domain.knowledgegraph.briefing.BriefingRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingSourceRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingStatus
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkTargetType
import com.briefy.api.infrastructure.mcp.CurrentMcpUser
import com.briefy.api.infrastructure.mcp.McpJson
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.function.Function

@Component
class GetBriefingTool(
    private val briefingRepository: BriefingRepository,
    private val briefingSourceRepository: BriefingSourceRepository,
    private val briefingReferenceRepository: BriefingReferenceRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val topicRepository: TopicRepository,
    private val mcpJson: McpJson,
) {
    data class Input(val id: String)

    data class Reference(val url: String, val title: String, val snippet: String?)
    data class Result(
        val id: UUID,
        val title: String?,
        val synthesizedText: String?,
        val sourceIds: List<UUID>,
        val references: List<Reference>,
        val topics: List<String>,
        val createdAt: Instant,
    )

    fun toolCallback(): ToolCallback = FunctionToolCallback.builder(
        "get_briefing",
        Function<Input, String> { execute(it) }
    )
        .description("Fetch the full synthesized text and citation references of a single briefing by ID. Includes source IDs, references with url/title/snippet, and assigned topics.")
        .inputType(Input::class.java)
        .build()

    private fun execute(input: Input): String {
        val userId = CurrentMcpUser.userId()
        val briefingId = runCatching { UUID.fromString(input.id) }.getOrNull()
            ?: return mcpJson.stringify(mapOf("error" to "Invalid briefing id"))

        val briefing = briefingRepository.findByIdAndUserId(briefingId, userId)
            ?: return mcpJson.stringify(mapOf("error" to "Briefing not found"))

        if (briefing.status != BriefingStatus.READY) {
            return mcpJson.stringify(mapOf("error" to "Briefing not available"))
        }

        val sourceIds = briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id).map { it.sourceId }
        val references = briefingReferenceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)
            .filter { it.status == BriefingReferenceStatus.ACTIVE }
            .map { Reference(url = it.url, title = it.title, snippet = it.snippet) }

        val topicLinks = topicLinkRepository.findByUserIdAndTargetTypeAndTargetIdAndStatusOrderByAssignedAtDesc(
            userId, TopicLinkTargetType.BRIEFING, briefing.id, TopicLinkStatus.ACTIVE
        )
        val topics = if (topicLinks.isNotEmpty()) {
            topicRepository.findAllByIdInAndUserId(topicLinks.map { it.topicId }.distinct(), userId).map { it.name }
        } else emptyList()

        return mcpJson.stringify(
            Result(
                id = briefing.id,
                title = briefing.title,
                synthesizedText = briefing.contentMarkdown,
                sourceIds = sourceIds,
                references = references,
                topics = topics,
                createdAt = briefing.createdAt,
            )
        )
    }
}
