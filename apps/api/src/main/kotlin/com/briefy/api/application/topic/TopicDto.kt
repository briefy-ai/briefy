package com.briefy.api.application.topic

import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.topic.Topic
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLink
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class SourceTopicSuggestionResponse(
    val topicLinkId: UUID,
    val topicId: UUID,
    val topicName: String,
    val topicStatus: String,
    val confidence: BigDecimal?,
    val createdAt: Instant
)

data class SourceActiveTopicResponse(
    val topicId: UUID,
    val topicName: String,
    val topicStatus: String,
    val origin: String,
    val linkedAt: Instant
)

data class TopicSummaryResponse(
    val id: UUID,
    val name: String,
    val status: String,
    val origin: String,
    val linkedSourcesCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class TopicDetailResponse(
    val id: UUID,
    val name: String,
    val status: String,
    val origin: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val linkedSources: List<TopicLinkedSourceResponse>
)

data class TopicLinkedSourceResponse(
    val id: UUID,
    val normalizedUrl: String,
    val title: String?,
    val sourceType: String,
    val status: String,
    val createdAt: Instant
)

fun TopicLink.toSuggestionResponse(topic: Topic): SourceTopicSuggestionResponse = SourceTopicSuggestionResponse(
    topicLinkId = id,
    topicId = topic.id,
    topicName = topic.name,
    topicStatus = topic.status.name.lowercase(),
    confidence = suggestionConfidence,
    createdAt = createdAt
)

fun TopicLink.toActiveTopicResponse(topic: Topic): SourceActiveTopicResponse = SourceActiveTopicResponse(
    topicId = topic.id,
    topicName = topic.name,
    topicStatus = topic.status.name.lowercase(),
    origin = topic.origin.name.lowercase(),
    linkedAt = assignedAt
)

fun Topic.toSummaryResponse(linkedSourcesCount: Long): TopicSummaryResponse = TopicSummaryResponse(
    id = id,
    name = name,
    status = status.name.lowercase(),
    origin = origin.name.lowercase(),
    linkedSourcesCount = linkedSourcesCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Topic.toDetailResponse(linkedSources: List<Source>): TopicDetailResponse = TopicDetailResponse(
    id = id,
    name = name,
    status = status.name.lowercase(),
    origin = origin.name.lowercase(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    linkedSources = linkedSources.map {
        TopicLinkedSourceResponse(
            id = it.id,
            normalizedUrl = it.url.normalized,
            title = it.metadata?.title,
            sourceType = it.sourceType.name.lowercase(),
            status = it.status.name.lowercase(),
            createdAt = it.createdAt
        )
    }
)
