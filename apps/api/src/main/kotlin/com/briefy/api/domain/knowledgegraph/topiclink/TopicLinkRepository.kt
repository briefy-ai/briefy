package com.briefy.api.domain.knowledgegraph.topiclink

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID
import com.briefy.api.domain.knowledgegraph.source.SourceStatus

@Repository
interface TopicLinkRepository : JpaRepository<TopicLink, UUID> {
    fun findByUserIdAndTopicIdAndTargetTypeAndTargetIdAndStatusIn(
        userId: UUID,
        topicId: UUID,
        targetType: TopicLinkTargetType,
        targetId: UUID,
        statuses: Collection<TopicLinkStatus>
    ): TopicLink?

    fun findByUserIdAndTargetTypeAndTargetIdAndStatusOrderByAssignedAtDesc(
        userId: UUID,
        targetType: TopicLinkTargetType,
        targetId: UUID,
        status: TopicLinkStatus
    ): List<TopicLink>

    fun findByUserIdAndTargetTypeAndTargetIdAndStatusIn(
        userId: UUID,
        targetType: TopicLinkTargetType,
        targetId: UUID,
        statuses: Collection<TopicLinkStatus>
    ): List<TopicLink>

    fun findByUserIdAndTopicIdAndTargetTypeAndStatusOrderByAssignedAtDesc(
        userId: UUID,
        topicId: UUID,
        targetType: TopicLinkTargetType,
        status: TopicLinkStatus
    ): List<TopicLink>

    @Query(
        """
        select tl
        from TopicLink tl
        join Source s on s.id = tl.targetId and s.userId = tl.userId
        where tl.userId = :userId
          and tl.topicId = :topicId
          and tl.targetType = :targetType
          and tl.status = :topicLinkStatus
          and s.status = :sourceStatus
        order by tl.assignedAt desc
        """
    )
    fun findByUserIdAndTopicIdAndTargetTypeAndStatusAndSourceStatusOrderByAssignedAtDesc(
        @Param("userId") userId: UUID,
        @Param("topicId") topicId: UUID,
        @Param("targetType") targetType: TopicLinkTargetType,
        @Param("topicLinkStatus") topicLinkStatus: TopicLinkStatus,
        @Param("sourceStatus") sourceStatus: SourceStatus
    ): List<TopicLink>

    fun findAllByIdInAndUserIdAndTargetTypeAndTargetId(
        ids: Collection<UUID>,
        userId: UUID,
        targetType: TopicLinkTargetType,
        targetId: UUID
    ): List<TopicLink>

    fun countByUserIdAndTopicIdAndStatusIn(
        userId: UUID,
        topicId: UUID,
        statuses: Collection<TopicLinkStatus>
    ): Long

    @Query(
        """
        select tl.topicId as topicId, count(tl.id) as linkCount
        from TopicLink tl
        where tl.userId = :userId
          and tl.topicId in :topicIds
          and tl.targetType = :targetType
          and tl.status = :status
        group by tl.topicId
        """
    )
    fun countByTopicIdsAndStatus(
        @Param("userId") userId: UUID,
        @Param("topicIds") topicIds: Collection<UUID>,
        @Param("targetType") targetType: TopicLinkTargetType,
        @Param("status") status: TopicLinkStatus
    ): List<TopicLinkCountProjection>

    @Query(
        """
        select tl.topicId as topicId, count(tl.id) as linkCount
        from TopicLink tl
        join Source s on s.id = tl.targetId and s.userId = tl.userId
        where tl.userId = :userId
          and tl.topicId in :topicIds
          and tl.targetType = :targetType
          and tl.status = :topicLinkStatus
          and s.status = :sourceStatus
        group by tl.topicId
        """
    )
    fun countByTopicIdsAndStatusAndSourceStatus(
        @Param("userId") userId: UUID,
        @Param("topicIds") topicIds: Collection<UUID>,
        @Param("targetType") targetType: TopicLinkTargetType,
        @Param("topicLinkStatus") topicLinkStatus: TopicLinkStatus,
        @Param("sourceStatus") sourceStatus: SourceStatus
    ): List<TopicLinkCountProjection>

    @Query(
        """
        select tl.targetId as sourceId, count(tl.id) as linkCount
        from TopicLink tl
        where tl.userId = :userId
          and tl.targetType = :targetType
          and tl.status = :status
          and tl.targetId in :sourceIds
        group by tl.targetId
        """
    )
    fun countBySourceIdsAndStatus(
        @Param("userId") userId: UUID,
        @Param("sourceIds") sourceIds: Collection<UUID>,
        @Param("targetType") targetType: TopicLinkTargetType,
        @Param("status") status: TopicLinkStatus
    ): List<SourceTopicSuggestionCountProjection>
}

interface TopicLinkCountProjection {
    val topicId: UUID
    val linkCount: Long
}

interface SourceTopicSuggestionCountProjection {
    val sourceId: UUID
    val linkCount: Long
}
