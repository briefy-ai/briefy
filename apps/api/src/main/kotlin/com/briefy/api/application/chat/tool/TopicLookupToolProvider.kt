package com.briefy.api.application.chat.tool

import com.briefy.api.application.topic.TopicNotFoundException
import com.briefy.api.application.topic.TopicService
import com.briefy.api.application.topic.TopicSort
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.topic.TopicStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkTargetType
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class TopicLookupToolProvider(
    private val topicService: TopicService,
    private val topicLinkRepository: TopicLinkRepository,
    private val currentUserProvider: CurrentUserProvider
) : TopicLookupTool {

    @Transactional(readOnly = true)
    override fun lookup(request: TopicLookupRequest): TopicLookupResult {
        return request.topicId?.let(::lookupTopicDetail) ?: lookupTopicList(request)
    }

    private fun lookupTopicList(request: TopicLookupRequest): TopicLookupResult {
        val status = parseStatus(request.status) ?: return TopicLookupError(
            "Invalid 'status' argument for topic_lookup. Expected ACTIVE, SUGGESTED, or ARCHIVED."
        )
        val sort = TopicSort.from(request.orderBy) ?: return TopicLookupError(
            "Invalid 'orderBy' argument for topic_lookup. Expected one of: ${TopicSort.valuesForPrompt}."
        )
        val topics = topicService.listTopics(status, request.filter, sort)
        val truncated = topics.size > MAX_TOPICS
        val limitedTopics = topics.take(MAX_TOPICS)
        val sourceIdsByTopicId = if (request.includeSourceIds && limitedTopics.isNotEmpty()) {
            loadSourceIdsByTopicId(
                topicIds = limitedTopics.map { it.id },
                status = status
            )
        } else {
            emptyMap()
        }

        return TopicList(
            topics = limitedTopics.map { topic ->
                TopicListItem(
                    id = topic.id,
                    name = topic.name,
                    status = topic.status,
                    sourceCount = topic.linkedSourcesCount,
                    sourceIds = sourceIdsByTopicId[topic.id]?.takeIf { request.includeSourceIds }
                )
            },
            truncated = truncated,
            hint = if (truncated) {
                "Results truncated to 50 topics. Narrow the search with 'filter' or 'status'."
            } else {
                null
            }
        )
    }

    private fun lookupTopicDetail(topicId: UUID): TopicLookupResult {
        val topicWithSources = try {
            topicService.getTopicWithSources(topicId)
        } catch (_: TopicNotFoundException) {
            return TopicLookupError("Topic not found for topic_lookup.")
        }

        return TopicDetail(
            id = topicWithSources.topic.id,
            name = topicWithSources.topic.name,
            status = topicWithSources.topic.status.name.lowercase(),
            sources = topicWithSources.sources.map { source ->
                TopicSourceDetail(
                    id = source.id,
                    title = source.metadata?.title,
                    url = source.url.normalized,
                    sourceType = source.sourceType.name.lowercase(),
                    isRead = source.isRead,
                    wordCount = source.content?.wordCount ?: 0
                )
            }
        )
    }

    private fun loadSourceIdsByTopicId(
        topicIds: List<UUID>,
        status: TopicStatus
    ): Map<UUID, List<UUID>> {
        val linkStatus = when (status) {
            TopicStatus.SUGGESTED -> TopicLinkStatus.SUGGESTED
            TopicStatus.ACTIVE -> TopicLinkStatus.ACTIVE
            TopicStatus.ARCHIVED -> TopicLinkStatus.ACTIVE
        }

        return topicLinkRepository.findSourceIdsByTopicIds(
            userId = currentUserProvider.requireUserId(),
            topicIds = topicIds,
            targetType = TopicLinkTargetType.SOURCE,
            topicLinkStatus = linkStatus,
            sourceStatus = SourceStatus.ACTIVE
        )
            .groupBy { it.topicId }
            .mapValues { (_, projections) -> projections.map { it.sourceId } }
    }

    private fun parseStatus(status: String?): TopicStatus? {
        if (status.isNullOrBlank()) {
            return TopicStatus.ACTIVE
        }
        return runCatching { TopicStatus.valueOf(status.trim().uppercase()) }.getOrNull()
    }

    companion object {
        private const val MAX_TOPICS = 50
    }
}
