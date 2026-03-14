package com.briefy.api.domain.knowledgegraph.source

import com.briefy.api.application.source.SourceListCursor
import com.briefy.api.application.source.SourceSortStrategy
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkTargetType
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.nio.ByteBuffer
import java.util.UUID

@Repository
class SourceRepositoryCustomImpl(
    @PersistenceContext private val entityManager: EntityManager
) : SourceRepositoryCustom {

    override fun findSourcesPage(
        userId: UUID,
        status: SourceStatus,
        topicIds: List<UUID>?,
        sourceType: SourceType?,
        sort: SourceSortStrategy,
        cursor: SourceListCursor?,
        limit: Int
    ): List<Source> {
        val normalizedTopicIds = topicIds?.distinct()?.takeIf { it.isNotEmpty() }
        val jpql = buildString {
            append("SELECT s FROM Source s WHERE s.userId = :userId AND s.status = :status")
            if (normalizedTopicIds != null) {
                append(" ")
                append(
                    """
                    AND s.id IN (
                        SELECT tl.targetId
                        FROM TopicLink tl
                        WHERE tl.topicId IN :topicIds
                          AND tl.targetType = :targetType
                          AND tl.status = :topicLinkStatus
                          AND tl.userId = :userId
                    )
                    """.trimIndent()
                )
            }
            if (sourceType != null) {
                append(" AND s.sourceType = :sourceType")
            }
            appendCursorCondition(this, sort, cursor)
            append(" ORDER BY ${orderByClause(sort)}")
        }

        val query = entityManager.createQuery(jpql, Source::class.java)
            .setParameter("userId", userId)
            .setParameter("status", status)

        if (normalizedTopicIds != null) {
            query.setParameter("topicIds", normalizedTopicIds)
            query.setParameter("targetType", TopicLinkTargetType.SOURCE)
            query.setParameter("topicLinkStatus", TopicLinkStatus.ACTIVE)
        }
        if (sourceType != null) {
            query.setParameter("sourceType", sourceType)
        }
        bindCursorParameters(query, sort, cursor)
        query.maxResults = limit + 1
        return query.resultList
    }

    override fun searchSources(userId: UUID, query: String, limit: Int): List<SourceSearchProjection> {
        val sql = """
            SELECT DISTINCT s.id, s.metadata_title, s.metadata_author, s.url_platform, s.source_type, s.updated_at
            FROM sources s
            LEFT JOIN topic_links tl
              ON tl.target_id = s.id
             AND tl.target_type = 'SOURCE'
             AND tl.status = 'ACTIVE'
             AND tl.user_id = :userId
            LEFT JOIN topics t
              ON t.id = tl.topic_id
            WHERE s.user_id = :userId
              AND s.status = 'ACTIVE'
              AND (
                s.metadata_title ILIKE :pattern
                OR s.metadata_author ILIKE :pattern
                OR s.url_platform ILIKE :pattern
                OR t.name ILIKE :pattern
              )
            ORDER BY s.updated_at DESC, s.id DESC
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("pattern", "%${query.trim()}%")
            .setMaxResults(limit)
            .resultList as List<Array<Any?>>

        return rows.map { row ->
            SourceSearchProjection(
                id = toUuid(row[0]),
                title = row[1] as String?,
                author = row[2] as String?,
                domain = row[3] as String?,
                sourceType = SourceType.valueOf(row[4].toString())
            )
        }
    }

    private fun appendCursorCondition(
        query: StringBuilder,
        sort: SourceSortStrategy,
        cursor: SourceListCursor?
    ) {
        if (cursor == null) {
            return
        }

        when (sort) {
            SourceSortStrategy.NEWEST -> query.append(
                " AND (s.updatedAt < :cursorInstant OR (s.updatedAt = :cursorInstant AND s.id < :cursorId))"
            )

            SourceSortStrategy.OLDEST -> query.append(
                " AND (s.createdAt > :cursorInstant OR (s.createdAt = :cursorInstant AND s.id > :cursorId))"
            )

            SourceSortStrategy.LONGEST_READ -> {
                if (cursor.readingTime == null) {
                    query.append(" AND s.metadata.estimatedReadingTime IS NULL AND s.id < :cursorId")
                } else {
                    query.append(" ")
                    query.append(
                        """
                        AND (
                            s.metadata.estimatedReadingTime IS NULL
                            OR s.metadata.estimatedReadingTime < :cursorReadingTime
                            OR (s.metadata.estimatedReadingTime = :cursorReadingTime AND s.id < :cursorId)
                        )
                        """.trimIndent()
                    )
                }
            }

            SourceSortStrategy.SHORTEST_READ -> {
                if (cursor.readingTime == null) {
                    query.append(" AND s.metadata.estimatedReadingTime IS NULL AND s.id > :cursorId")
                } else {
                    query.append(" ")
                    query.append(
                        """
                        AND (
                            s.metadata.estimatedReadingTime IS NULL
                            OR s.metadata.estimatedReadingTime > :cursorReadingTime
                            OR (s.metadata.estimatedReadingTime = :cursorReadingTime AND s.id > :cursorId)
                        )
                        """.trimIndent()
                    )
                }
            }
        }
    }

    private fun bindCursorParameters(
        query: jakarta.persistence.TypedQuery<Source>,
        sort: SourceSortStrategy,
        cursor: SourceListCursor?
    ) {
        if (cursor == null) {
            return
        }
        query.setParameter("cursorId", cursor.id)
        when (sort) {
            SourceSortStrategy.NEWEST,
            SourceSortStrategy.OLDEST -> query.setParameter("cursorInstant", cursor.instantValue)

            SourceSortStrategy.LONGEST_READ,
            SourceSortStrategy.SHORTEST_READ -> {
                if (cursor.readingTime != null) {
                    query.setParameter("cursorReadingTime", cursor.readingTime)
                }
            }
        }
    }

    private fun orderByClause(sort: SourceSortStrategy): String {
        return when (sort) {
            SourceSortStrategy.NEWEST -> "s.updatedAt DESC, s.id DESC"
            SourceSortStrategy.OLDEST -> "s.createdAt ASC, s.id ASC"
            SourceSortStrategy.LONGEST_READ -> """
                CASE WHEN s.metadata.estimatedReadingTime IS NULL THEN 1 ELSE 0 END ASC,
                s.metadata.estimatedReadingTime DESC,
                s.id DESC
            """.trimIndent()
            SourceSortStrategy.SHORTEST_READ -> """
                CASE WHEN s.metadata.estimatedReadingTime IS NULL THEN 1 ELSE 0 END ASC,
                s.metadata.estimatedReadingTime ASC,
                s.id ASC
            """.trimIndent()
        }
    }

    private fun toUuid(value: Any?): UUID {
        return when (value) {
            is UUID -> value
            is ByteArray -> {
                val buffer = ByteBuffer.wrap(value)
                UUID(buffer.long, buffer.long)
            }
            else -> UUID.fromString(value.toString())
        }
    }
}
