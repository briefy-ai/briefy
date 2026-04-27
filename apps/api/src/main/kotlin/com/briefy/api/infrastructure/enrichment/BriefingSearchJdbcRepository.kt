package com.briefy.api.infrastructure.enrichment

import com.briefy.api.domain.knowledgegraph.briefing.BriefingSearchHit
import com.briefy.api.domain.knowledgegraph.briefing.BriefingSearchRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class BriefingSearchJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : BriefingSearchRepository {

    override fun searchReady(
        userId: UUID,
        query: String,
        topicId: UUID?,
        limit: Int
    ): List<BriefingSearchHit> {
        val effectiveLimit = limit.coerceAtLeast(0)
        if (effectiveLimit == 0) return emptyList()

        val pattern = "%${escapeLike(query.trim())}%"

        val sql = buildString {
            appendLine("""
                SELECT b.id, b.title, b.content_markdown, b.created_at
                FROM briefings b
                WHERE b.user_id = :userId
                  AND b.status = 'READY'
                  AND (
                    COALESCE(b.title, '') ILIKE :pattern ESCAPE '\'
                    OR COALESCE(b.content_markdown, '') ILIKE :pattern ESCAPE '\'
                  )
            """.trimIndent())
            if (topicId != null) {
                appendLine("""
                  AND EXISTS (
                    SELECT 1 FROM topic_links tl
                    WHERE tl.target_type = 'BRIEFING'
                      AND tl.target_id = b.id
                      AND tl.user_id = :userId
                      AND tl.status = 'ACTIVE'
                      AND tl.topic_id = :topicId
                  )
                """.trimIndent())
            }
            appendLine("ORDER BY b.created_at DESC")
            appendLine("LIMIT :limit")
        }

        val params = MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("pattern", pattern)
            .addValue("limit", effectiveLimit)
        if (topicId != null) params.addValue("topicId", topicId)

        return jdbcTemplate.query(sql, params) { rs, _ ->
            BriefingSearchHit(
                id = UUID.fromString(rs.getString("id")),
                title = rs.getString("title"),
                contentMarkdown = rs.getString("content_markdown"),
                createdAt = rs.getTimestamp("created_at").toInstant()
            )
        }
    }

    private fun escapeLike(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}
