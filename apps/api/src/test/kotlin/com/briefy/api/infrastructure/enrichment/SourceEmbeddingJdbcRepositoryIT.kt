package com.briefy.api.infrastructure.enrichment

import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceEmbeddingRepository
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Testcontainers
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "ai.observability.enabled=false"
    ]
)
@Transactional
class SourceEmbeddingJdbcRepositoryIT {

    @Autowired
    lateinit var sourceEmbeddingRepository: SourceEmbeddingRepository

    @Autowired
    lateinit var sourceRepository: SourceRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanup() {
        jdbcTemplate.update("DELETE FROM source_embeddings")
        jdbcTemplate.update("DELETE FROM sources")
        jdbcTemplate.update("DELETE FROM users")
    }

    @Test
    fun `findSimilar returns cosine-ordered active sources scoped by user`() {
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        insertUser(userId, "owner@example.com")
        insertUser(otherUserId, "other@example.com")

        val sourceA = createActiveSource(userId, "https://example.com/a", "Source A")
        val sourceB = createActiveSource(userId, "https://example.com/b", "Source B")
        val sourceArchived = createArchivedSource(userId, "https://example.com/archived", "Archived")
        val sourceOtherUser = createActiveSource(otherUserId, "https://example.com/other", "Other User")

        sourceEmbeddingRepository.upsert(sourceA.id, userId, vectorOf(1.0, 0.0), Instant.now())
        sourceEmbeddingRepository.upsert(sourceB.id, userId, vectorOf(0.8, 0.2), Instant.now())
        sourceEmbeddingRepository.upsert(sourceArchived.id, userId, vectorOf(1.0, 0.0), Instant.now())
        sourceEmbeddingRepository.upsert(sourceOtherUser.id, otherUserId, vectorOf(1.0, 0.0), Instant.now())

        val result = sourceEmbeddingRepository.findSimilar(
            userId = userId,
            queryEmbedding = vectorOf(1.0, 0.0),
            limit = 10,
            excludeSourceId = sourceA.id
        )

        assertEquals(1, result.size)
        assertEquals(sourceB.id, result.first().sourceId)
        assertTrue(result.first().score > 0.0)
        assertEquals("Source B", result.first().title)
    }

    private fun createActiveSource(userId: UUID, url: String, title: String): Source {
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = url,
            userId = userId
        )
        source.startExtraction()
        val content = Content.from("content for $title")
        source.completeExtraction(
            content,
            Metadata.from(
                title = title,
                author = "Author",
                publishedDate = Instant.parse("2025-01-01T00:00:00Z"),
                platform = "web",
                wordCount = content.wordCount,
                aiFormatted = true,
                extractionProvider = "jsoup"
            )
        )
        return sourceRepository.saveAndFlush(source)
    }

    private fun createArchivedSource(userId: UUID, url: String, title: String): Source {
        val source = createActiveSource(userId, url, title)
        source.archive()
        return sourceRepository.saveAndFlush(source)
    }

    private fun insertUser(userId: UUID, email: String) {
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, role, status, auth_provider, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            userId,
            email,
            "hash",
            "USER",
            "ACTIVE",
            "LOCAL"
        )
    }

    private fun vectorOf(first: Double, second: Double): List<Double> {
        return MutableList(1536) { 0.0 }.apply {
            this[0] = first
            this[1] = second
        }
    }

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("briefy")
            .withUsername("briefy")
            .withPassword("briefy")

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
