package com.briefy.api.infrastructure.enrichment

import com.briefy.api.domain.knowledgegraph.source.SourceEmbeddingRepository
import com.briefy.api.domain.knowledgegraph.source.SourceSimilarityMatch
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class SourceEmbeddingJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : SourceEmbeddingRepository {

    override fun upsert(sourceId: UUID, userId: UUID, embedding: List<Double>, now: Instant) {
        val sql = """
            INSERT INTO source_embeddings (source_id, user_id, embedding, created_at, updated_at)
            VALUES (:sourceId, :userId, CAST(:embedding AS vector), :createdAt, :updatedAt)
            ON CONFLICT (source_id)
            DO UPDATE SET
                user_id = EXCLUDED.user_id,
                embedding = EXCLUDED.embedding,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("sourceId", sourceId)
            .addValue("userId", userId)
            .addValue("embedding", toVectorLiteral(embedding))
            .addValue("createdAt", Timestamp.from(now))
            .addValue("updatedAt", Timestamp.from(now))

        jdbcTemplate.update(sql, params)
    }

    override fun findSimilar(
        userId: UUID,
        queryEmbedding: List<Double>,
        limit: Int,
        excludeSourceId: UUID?
    ): List<SourceSimilarityMatch> {
        if (limit <= 0) {
            return emptyList()
        }

        val sql = """
            SELECT
                s.id AS source_id,
                (1 - (se.embedding <=> CAST(:queryEmbedding AS vector))) AS similarity_score,
                s.metadata_title AS metadata_title,
                s.url_normalized AS url_normalized,
                COALESCE(s.content_word_count, 0) AS content_word_count
            FROM source_embeddings se
            JOIN sources s ON s.id = se.source_id
            WHERE se.user_id = :userId
              AND s.user_id = :userId
              AND s.status = 'ACTIVE'
              AND (:excludeSourceId IS NULL OR s.id <> :excludeSourceId)
            ORDER BY se.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :limit
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("queryEmbedding", toVectorLiteral(queryEmbedding))
            .addValue("excludeSourceId", excludeSourceId)
            .addValue("limit", limit)

        return jdbcTemplate.query(sql, params) { rs, _ ->
            SourceSimilarityMatch(
                sourceId = UUID.fromString(rs.getString("source_id")),
                score = rs.getDouble("similarity_score"),
                title = rs.getString("metadata_title"),
                urlNormalized = rs.getString("url_normalized"),
                wordCount = rs.getInt("content_word_count")
            )
        }
    }

    private fun toVectorLiteral(values: List<Double>): String {
        require(values.isNotEmpty()) { "embedding must not be empty" }
        require(values.all { it.isFinite() }) { "embedding contains non-finite values" }
        return values.joinToString(prefix = "[", postfix = "]") { it.toString() }
    }
}
