package com.briefy.api.application.topic

import com.briefy.api.application.source.InvalidSourceStateException
import com.briefy.api.application.source.SourceNotFoundException
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.topic.Topic
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topic.TopicStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLink
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkTargetType
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TopicService(
    private val sourceRepository: SourceRepository,
    private val topicRepository: TopicRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val idGenerator: IdGenerator
) {
    private val logger = LoggerFactory.getLogger(TopicService::class.java)

    @Transactional(readOnly = true)
    fun listSourceTopicSuggestions(sourceId: UUID): List<SourceTopicSuggestionResponse> {
        val userId = currentUserProvider.requireUserId()
        ensureSourceIsActive(userId, sourceId)

        val links = topicLinkRepository.findByUserIdAndTargetTypeAndTargetIdAndStatusOrderByAssignedAtDesc(
            userId = userId,
            targetType = TopicLinkTargetType.SOURCE,
            targetId = sourceId,
            status = TopicLinkStatus.SUGGESTED
        )
        if (links.isEmpty()) {
            return emptyList()
        }

        val topicsById = topicRepository.findAllByIdInAndUserId(
            ids = links.map { it.topicId }.distinct(),
            userId = userId
        ).associateBy { it.id }

        return links.mapNotNull { link ->
            val topic = topicsById[link.topicId] ?: return@mapNotNull null
            link.toSuggestionResponse(topic)
        }
    }

    @Transactional(readOnly = true)
    fun listSourceActiveTopics(sourceId: UUID): List<SourceActiveTopicResponse> {
        val userId = currentUserProvider.requireUserId()
        ensureSourceIsActive(userId, sourceId)

        val links = topicLinkRepository.findByUserIdAndTargetTypeAndTargetIdAndStatusOrderByAssignedAtDesc(
            userId = userId,
            targetType = TopicLinkTargetType.SOURCE,
            targetId = sourceId,
            status = TopicLinkStatus.ACTIVE
        )
        if (links.isEmpty()) {
            return emptyList()
        }

        val topicsById = topicRepository.findAllByIdInAndUserId(
            ids = links.map { it.topicId }.distinct(),
            userId = userId
        ).associateBy { it.id }

        return links.mapNotNull { link ->
            val topic = topicsById[link.topicId] ?: return@mapNotNull null
            link.toActiveTopicResponse(topic)
        }
    }

    @Transactional
    fun applySourceTopicSuggestions(sourceId: UUID, keepTopicLinkIds: List<UUID>) {
        val userId = currentUserProvider.requireUserId()
        ensureSourceIsActive(userId, sourceId)

        val pendingLinks = topicLinkRepository.findByUserIdAndTargetTypeAndTargetIdAndStatusOrderByAssignedAtDesc(
            userId = userId,
            targetType = TopicLinkTargetType.SOURCE,
            targetId = sourceId,
            status = TopicLinkStatus.SUGGESTED
        )
        if (pendingLinks.isEmpty()) {
            require(keepTopicLinkIds.isEmpty()) { "keepTopicLinkIds must reference pending suggestions for this source" }
            return
        }

        val keepIds = keepTopicLinkIds.distinct().toSet()
        val validIds = pendingLinks.map { it.id }.toSet()
        require(keepIds.all(validIds::contains)) { "keepTopicLinkIds must reference pending suggestions for this source" }

        val topicsById = topicRepository.findAllByIdInAndUserId(
            ids = pendingLinks.map { it.topicId }.distinct(),
            userId = userId
        ).associateBy { it.id }

        val candidateTopicIds = mutableSetOf<UUID>()
        pendingLinks.forEach { link ->
            if (keepIds.contains(link.id)) {
                link.confirm()
                val topic = topicsById[link.topicId] ?: throw TopicNotFoundException(link.topicId)
                if (topic.status != TopicStatus.ACTIVE) {
                    topic.activate()
                }
            } else {
                link.remove()
                candidateTopicIds.add(link.topicId)
            }
        }

        topicLinkRepository.saveAll(pendingLinks)
        topicRepository.saveAll(topicsById.values)
        archiveOrphanSuggestedTopics(userId, candidateTopicIds)

        logger.info(
            "[topic] applied suggestions sourceId={} userId={} kept={} dismissed={}",
            sourceId,
            userId,
            keepIds.size,
            pendingLinks.size - keepIds.size
        )
    }

    @Transactional
    fun addManualTopicSuggestionToSource(sourceId: UUID, rawName: String): SourceTopicSuggestionResponse {
        val userId = currentUserProvider.requireUserId()
        val source = ensureSourceIsActive(userId, sourceId)
        val topicName = rawName.trim()
        require(topicName.isNotBlank()) { "name must not be blank" }

        val topic = resolveOrCreateTopic(userId, topicName, failIfActiveExists = false)
        val existingLink = topicLinkRepository.findByUserIdAndTopicIdAndTargetTypeAndTargetIdAndStatusIn(
            userId = userId,
            topicId = topic.id,
            targetType = TopicLinkTargetType.SOURCE,
            targetId = source.id,
            statuses = listOf(TopicLinkStatus.SUGGESTED, TopicLinkStatus.ACTIVE)
        )

        if (existingLink != null) {
            if (existingLink.status == TopicLinkStatus.ACTIVE) {
                throw TopicAlreadyLinkedToSourceException(topic.name)
            }
            return existingLink.toSuggestionResponse(topic)
        }

        val link = TopicLink.suggestedUserForSource(
            id = idGenerator.newId(),
            topicId = topic.id,
            sourceId = source.id,
            userId = userId
        )
        topicLinkRepository.save(link)
        logger.info("[topic] manual suggestion added sourceId={} userId={} topicId={}", sourceId, userId, topic.id)
        return link.toSuggestionResponse(topic)
    }

    @Transactional
    fun createTopic(rawName: String, sourceIds: List<UUID>): TopicSummaryResponse {
        val userId = currentUserProvider.requireUserId()
        val topicName = rawName.trim()
        require(topicName.isNotBlank()) { "name must not be blank" }

        val selectedSourceIds = sourceIds.distinct()
        val selectedSources = loadActiveSourcesOrThrow(userId, selectedSourceIds)
        val topic = resolveOrCreateTopic(userId, topicName, failIfActiveExists = true)

        if (selectedSources.isNotEmpty()) {
            upsertActiveTopicLinks(userId, topic, selectedSources.map { it.id })
        }

        val linkedSourcesCount = topicLinkRepository.countByTopicIdsAndStatusAndSourceStatus(
            userId = userId,
            topicIds = listOf(topic.id),
            targetType = TopicLinkTargetType.SOURCE,
            topicLinkStatus = TopicLinkStatus.ACTIVE,
            sourceStatus = SourceStatus.ACTIVE
        ).firstOrNull()?.linkCount ?: 0L

        logger.info(
            "[topic] created topicId={} userId={} selectedSources={}",
            topic.id,
            userId,
            selectedSources.size
        )
        return topic.toSummaryResponse(linkedSourcesCount)
    }

    @Transactional(readOnly = true)
    fun listTopics(status: TopicStatus, query: String?): List<TopicSummaryResponse> {
        val userId = currentUserProvider.requireUserId()
        val topics = if (query.isNullOrBlank()) {
            topicRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, status)
        } else {
            topicRepository.findByUserIdAndStatusAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(userId, status, query.trim())
        }
        if (topics.isEmpty()) {
            return emptyList()
        }

        val linkStatus = when (status) {
            TopicStatus.SUGGESTED -> TopicLinkStatus.SUGGESTED
            TopicStatus.ACTIVE -> TopicLinkStatus.ACTIVE
            TopicStatus.ARCHIVED -> TopicLinkStatus.ACTIVE
        }

        val countsWithActiveSources = topicLinkRepository.countByTopicIdsAndStatusAndSourceStatus(
            userId = userId,
            topicIds = topics.map { it.id },
            targetType = TopicLinkTargetType.SOURCE,
            topicLinkStatus = linkStatus,
            sourceStatus = SourceStatus.ACTIVE
        ).associate { it.topicId to it.linkCount }

        return topics.map { topic ->
            topic.toSummaryResponse(linkedSourcesCount = countsWithActiveSources[topic.id] ?: 0L)
        }
    }

    @Transactional(readOnly = true)
    fun getTopic(id: UUID): TopicDetailResponse {
        val userId = currentUserProvider.requireUserId()
        val topic = topicRepository.findByIdAndUserId(id, userId) ?: throw TopicNotFoundException(id)
        val linkStatus = when (topic.status) {
            TopicStatus.SUGGESTED -> TopicLinkStatus.SUGGESTED
            TopicStatus.ACTIVE -> TopicLinkStatus.ACTIVE
            TopicStatus.ARCHIVED -> TopicLinkStatus.ACTIVE
        }

        val links = topicLinkRepository.findByUserIdAndTopicIdAndTargetTypeAndStatusAndSourceStatusOrderByAssignedAtDesc(
            userId = userId,
            topicId = id,
            targetType = TopicLinkTargetType.SOURCE,
            topicLinkStatus = linkStatus,
            sourceStatus = SourceStatus.ACTIVE
        )

        val sourcesById = sourceRepository.findAllByUserIdAndIdIn(userId, links.map { it.targetId }.distinct())
            .associateBy { it.id }
        val orderedSources = links.mapNotNull { sourcesById[it.targetId] }

        return topic.toDetailResponse(orderedSources)
    }

    private fun ensureSourceIsActive(userId: UUID, sourceId: UUID): Source {
        val source = sourceRepository.findByIdAndUserId(sourceId, userId)
            ?: throw SourceNotFoundException(sourceId)
        if (source.status != SourceStatus.ACTIVE) {
            throw InvalidSourceStateException(
                "Source must be active to manage topic suggestions. Current status: ${source.status}"
            )
        }
        return source
    }

    private fun archiveOrphanSuggestedTopics(userId: UUID, topicIds: Set<UUID>) {
        if (topicIds.isEmpty()) {
            return
        }

        val topics = topicRepository.findAllByIdInAndUserId(topicIds, userId)
        topics.forEach { topic ->
            if (topic.status == TopicStatus.SUGGESTED) {
                val liveLinks = topicLinkRepository.countByUserIdAndTopicIdAndStatusIn(
                    userId = userId,
                    topicId = topic.id,
                    statuses = listOf(TopicLinkStatus.SUGGESTED, TopicLinkStatus.ACTIVE)
                )
                if (liveLinks == 0L) {
                    topic.archive()
                }
            }
        }
        topicRepository.saveAll(topics)
    }

    private fun resolveOrCreateTopic(userId: UUID, topicName: String, failIfActiveExists: Boolean): Topic {
        val normalizedName = Topic.normalizeName(topicName)
        require(normalizedName.isNotBlank()) { "name must not be blank" }

        val existing = topicRepository.findByUserIdAndNameNormalized(userId, normalizedName)
        if (existing == null) {
            val created = Topic.activeUser(
                id = idGenerator.newId(),
                userId = userId,
                name = topicName
            )
            return topicRepository.save(created)
        }

        if (existing.status == TopicStatus.ACTIVE && failIfActiveExists) {
            throw TopicAlreadyExistsException(existing.name)
        }

        if (existing.status != TopicStatus.ACTIVE) {
            existing.activate()
            topicRepository.save(existing)
        }
        return existing
    }

    private fun loadActiveSourcesOrThrow(userId: UUID, sourceIds: List<UUID>): List<Source> {
        if (sourceIds.isEmpty()) {
            return emptyList()
        }
        val sources = sourceRepository.findAllByUserIdAndIdIn(userId, sourceIds)
        require(sources.size == sourceIds.size) { "One or more selected sources were not found" }
        require(sources.all { it.status == SourceStatus.ACTIVE }) {
            "Only active sources can be linked to a topic"
        }
        return sources
    }

    private fun upsertActiveTopicLinks(userId: UUID, topic: Topic, sourceIds: List<UUID>) {
        val linksToSave = mutableListOf<TopicLink>()
        sourceIds.forEach { sourceId ->
            val existingLink = topicLinkRepository.findByUserIdAndTopicIdAndTargetTypeAndTargetIdAndStatusIn(
                userId = userId,
                topicId = topic.id,
                targetType = TopicLinkTargetType.SOURCE,
                targetId = sourceId,
                statuses = listOf(TopicLinkStatus.SUGGESTED, TopicLinkStatus.ACTIVE)
            )

            when {
                existingLink == null -> {
                    linksToSave.add(
                        TopicLink.activeUserForSource(
                            id = idGenerator.newId(),
                            topicId = topic.id,
                            sourceId = sourceId,
                            userId = userId
                        )
                    )
                }
                existingLink.status == TopicLinkStatus.SUGGESTED -> {
                    existingLink.confirm()
                    linksToSave.add(existingLink)
                }
                else -> Unit
            }
        }
        if (linksToSave.isNotEmpty()) {
            topicLinkRepository.saveAll(linksToSave)
        }
    }
}
