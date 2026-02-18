package com.briefy.api.application.topic

import com.briefy.api.application.settings.UserAiSettingsService
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
import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class TopicSuggestionService(
    private val sourceRepository: SourceRepository,
    private val topicRepository: TopicRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val aiAdapter: AiAdapter,
    private val userAiSettingsService: UserAiSettingsService,
    private val objectMapper: ObjectMapper,
    private val idGenerator: IdGenerator
) {
    private val logger = LoggerFactory.getLogger(TopicSuggestionService::class.java)

    @Transactional
    fun generateForSource(sourceId: UUID, userId: UUID) {
        val source = sourceRepository.findByIdAndUserId(sourceId, userId) ?: return
        if (source.status != SourceStatus.ACTIVE || source.content == null) {
            return
        }

        val existingActiveTopics = topicRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(
            userId,
            TopicStatus.ACTIVE
        )
        val existingTopicsById = existingActiveTopics.associateBy { it.id }

        val candidates = try {
            generateCandidates(source, userId, existingActiveTopics)
        } catch (e: IllegalStateException) {
            logger.info(
                "[topic-suggestions] skipped sourceId={} userId={} reason={}",
                sourceId,
                userId,
                e.message
            )
            return
        } catch (e: Exception) {
            logger.warn(
                "[topic-suggestions] generation failed sourceId={} userId={} reason={}",
                sourceId,
                userId,
                e.message
            )
            return
        }

        if (candidates.isEmpty()) {
            logger.info("[topic-suggestions] no suggestions sourceId={} userId={}", sourceId, userId)
            return
        }

        var createdLinks = 0
        val processedTopicIds = mutableSetOf<UUID>()
        candidates.forEach { candidate ->
            val topic = resolveTopicCandidate(userId, candidate, existingTopicsById) ?: return@forEach
            if (!processedTopicIds.add(topic.id)) {
                return@forEach
            }

            val existingLink = topicLinkRepository.findByUserIdAndTopicIdAndTargetTypeAndTargetIdAndStatusIn(
                userId = userId,
                topicId = topic.id,
                targetType = TopicLinkTargetType.SOURCE,
                targetId = source.id,
                statuses = listOf(TopicLinkStatus.SUGGESTED, TopicLinkStatus.ACTIVE)
            )
            if (existingLink != null) {
                return@forEach
            }

            val link = TopicLink.suggestedForSource(
                id = idGenerator.newId(),
                topicId = topic.id,
                sourceId = source.id,
                userId = userId,
                confidence = candidate.confidence
            )
            topicLinkRepository.save(link)
            createdLinks++
        }

        logger.info(
            "[topic-suggestions] generated sourceId={} userId={} candidates={} createdLinks={}",
            sourceId,
            userId,
            candidates.size,
            createdLinks
        )
    }

    private fun generateCandidates(source: Source, userId: UUID, existingActiveTopics: List<Topic>): List<TopicCandidate> {
        val systemPrompt = """
            You are a strict topic extraction engine.
            Return only JSON with this shape:
            {"matches":[{"existingTopicId":"uuid","confidence":0.0}],"newTopics":[{"name":"string","confidence":0.0}]}
            Rules:
            - Return 1 to 5 concise topics.
            - Avoid duplicates and near-duplicates.
            - Reuse existing topics whenever semantically close.
            - Only create newTopics when no existing topic is a good fit.
            - existingTopicId must be copied exactly from the provided existing topics list.
            - Topic names must be 2 to 60 characters.
            - No explanation, no markdown, no extra keys.
        """.trimIndent()

        val inputText = source.content?.text?.take(MAX_CONTENT_CHARS).orEmpty()
        val existingTopicsJson = objectMapper.writeValueAsString(
            existingActiveTopics
                .take(MAX_EXISTING_TOPICS_IN_PROMPT)
                .map { mapOf("id" to it.id.toString(), "name" to it.name) }
        )
        val userPrompt = """
            Source title: ${source.metadata?.title ?: "unknown"}
            Source url: ${source.url.normalized}
            Existing user topics:
            $existingTopicsJson
            Source content:
            $inputText
        """.trimIndent()

        val selection = userAiSettingsService.resolveUseCaseSelection(userId, UserAiSettingsService.TOPIC_EXTRACTION)
        val raw = aiAdapter.complete(
            provider = selection.provider,
            model = selection.model,
            prompt = userPrompt,
            systemPrompt = systemPrompt,
            useCase = UserAiSettingsService.TOPIC_EXTRACTION
        )
        return parseCandidates(raw)
    }

    private fun parseCandidates(raw: String): List<TopicCandidate> {
        if (raw.isBlank()) return emptyList()

        val cleaned = stripCodeFences(raw)
        val json = objectMapper.readTree(cleaned)

        val topicNodes = when {
            json.isArray -> json
            json.isObject && json.has("topics") -> json.get("topics")
            json.isObject && (json.has("matches") || json.has("newTopics")) -> {
                val merged = mutableListOf<JsonNode>()
                val matches = json.get("matches")
                if (matches != null && matches.isArray) {
                    merged.addAll(matches.toList())
                }
                val newTopics = json.get("newTopics")
                if (newTopics != null && newTopics.isArray) {
                    merged.addAll(newTopics.toList())
                }
                objectMapper.valueToTree(merged)
            }
            else -> return emptyList()
        }
        if (topicNodes == null || !topicNodes.isArray) return emptyList()

        return topicNodes.mapNotNull { node -> parseCandidate(node) }
            .filter { candidate ->
                when {
                    candidate.existingTopicId != null -> true
                    candidate.name.isNullOrBlank() -> false
                    else -> (candidate.name?.length ?: 0) <= MAX_TOPIC_NAME_LENGTH
                }
            }
            .distinctBy {
                it.existingTopicId?.toString() ?: "name:${Topic.normalizeName(it.name.orEmpty())}"
            }
            .take(MAX_TOPICS)
    }

    private fun parseCandidate(node: JsonNode): TopicCandidate? {
        return when {
            node.isTextual -> TopicCandidate(
                existingTopicId = null,
                name = node.asText(),
                confidence = null
            )

            node.isObject -> {
                val existingTopicId = parseUuidOrNull(
                    node.path("existingTopicId").asText("").trim()
                ) ?: parseUuidOrNull(node.path("topicId").asText("").trim())
                val name = node.path("name").asText("").trim().ifBlank { null }
                if (existingTopicId == null && name == null) return null
                val confidence = if (node.has("confidence") && node.get("confidence").isNumber) {
                    normalizeConfidence(node.get("confidence").decimalValue())
                } else {
                    null
                }
                TopicCandidate(existingTopicId = existingTopicId, name = name, confidence = confidence)
            }

            else -> null
        }
    }

    private fun resolveTopicCandidate(
        userId: UUID,
        candidate: TopicCandidate,
        existingTopicsById: Map<UUID, Topic>
    ): Topic? {
        candidate.existingTopicId?.let { existingTopicId ->
            return existingTopicsById[existingTopicId]
        }

        val topicName = candidate.name ?: return null
        val normalizedName = Topic.normalizeName(topicName)
        if (normalizedName.isBlank()) {
            return null
        }

        return topicRepository.findByUserIdAndNameNormalized(userId, normalizedName)
            ?.also {
                if (it.status == TopicStatus.ARCHIVED) {
                    it.markSuggested()
                }
            }
            ?: Topic.suggestedSystem(
                id = idGenerator.newId(),
                userId = userId,
                name = topicName
            ).also { topicRepository.save(it) }
    }

    private fun normalizeConfidence(raw: BigDecimal): BigDecimal {
        return raw.coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
            .setScale(4, RoundingMode.HALF_UP)
    }

    private fun stripCodeFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed

        val withoutStart = trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .trim()
        return withoutStart.removeSuffix("```").trim()
    }

    private fun parseUuidOrNull(raw: String): UUID? {
        if (raw.isBlank()) return null
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private data class TopicCandidate(
        val existingTopicId: UUID?,
        val name: String?,
        val confidence: BigDecimal?
    )

    companion object {
        private const val MAX_TOPICS = 5
        private const val MAX_CONTENT_CHARS = 8_000
        private const val MAX_TOPIC_NAME_LENGTH = 200
        private const val MAX_EXISTING_TOPICS_IN_PROMPT = 100
    }
}
