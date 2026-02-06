package com.briefy.api.domain.knowledgegraph.topiclink

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "topic_links")
class TopicLink(
    @Id
    val id: UUID,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
)
