package com.briefy.api.application.enrichment

import com.briefy.api.domain.knowledgegraph.source.SourceEmbeddingRepository
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.infrastructure.ai.EmbeddingAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SourceEmbeddingService(
    private val sourceRepository: SourceRepository,
    private val sourceEmbeddingRepository: SourceEmbeddingRepository,
    private val embeddingAdapter: EmbeddingAdapter
) {
    private val logger = LoggerFactory.getLogger(SourceEmbeddingService::class.java)

    @Transactional
    fun generateForSource(sourceId: UUID, userId: UUID) {
        val source = sourceRepository.findByIdAndUserId(sourceId, userId)
        if (source == null) {
            logger.info("[embedding] skipped sourceId={} userId={} reason=source_not_found", sourceId, userId)
            return
        }

        if (source.status != SourceStatus.ACTIVE) {
            logger.info(
                "[embedding] skipped sourceId={} userId={} reason=source_not_active status={}",
                sourceId,
                userId,
                source.status
            )
            return
        }

        val text = source.content?.text?.trim().orEmpty()
        if (text.isBlank()) {
            logger.info("[embedding] skipped sourceId={} userId={} reason=blank_content", sourceId, userId)
            return
        }

        val embedding = try {
            embeddingAdapter.embed(text)
        } catch (e: Exception) {
            logger.warn(
                "[embedding] skipped sourceId={} userId={} reason=embedding_generation_failed",
                sourceId,
                userId,
                e
            )
            return
        }

        sourceEmbeddingRepository.upsert(
            sourceId = source.id,
            userId = source.userId,
            embedding = embedding,
            now = Instant.now()
        )
        logger.info(
            "[embedding] upserted sourceId={} userId={} embeddingDimension={}",
            source.id,
            source.userId,
            embedding.size
        )
    }
}
