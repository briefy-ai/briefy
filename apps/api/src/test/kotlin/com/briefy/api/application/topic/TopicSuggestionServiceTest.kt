package com.briefy.api.application.topic

import com.briefy.api.application.settings.AiModelSelection
import com.briefy.api.application.settings.UserAiSettingsService
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.TopicExtractionState
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topic.TopicStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class TopicSuggestionServiceTest {
    private val sourceRepository: SourceRepository = mock()
    private val topicRepository: TopicRepository = mock()
    private val topicLinkRepository: TopicLinkRepository = mock()
    private val aiAdapter: AiAdapter = mock()
    private val userAiSettingsService: UserAiSettingsService = mock()
    private val idGenerator: IdGenerator = mock()

    private val service = TopicSuggestionService(
        sourceRepository = sourceRepository,
        topicRepository = topicRepository,
        topicLinkRepository = topicLinkRepository,
        aiAdapter = aiAdapter,
        userAiSettingsService = userAiSettingsService,
        objectMapper = jacksonObjectMapper(),
        idGenerator = idGenerator
    )

    @Test
    fun `generateForSource marks source as succeeded when no candidates are returned`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(topicRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, TopicStatus.ACTIVE)).thenReturn(emptyList())
        whenever(userAiSettingsService.resolveUseCaseSelection(userId, UserAiSettingsService.TOPIC_EXTRACTION))
            .thenReturn(AiModelSelection(provider = "zhipuai", model = "glm-4.7-flash"))
        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenReturn("")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }

        service.generateForSource(source.id, userId)

        assertEquals(TopicExtractionState.SUCCEEDED, source.topicExtractionState)
        assertEquals(null, source.topicExtractionFailureReason)
        verify(sourceRepository).save(source)
    }

    @Test
    fun `generateForSource marks source as failed when topic generation throws`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(topicRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, TopicStatus.ACTIVE)).thenReturn(emptyList())
        whenever(userAiSettingsService.resolveUseCaseSelection(userId, UserAiSettingsService.TOPIC_EXTRACTION))
            .thenReturn(AiModelSelection(provider = "zhipuai", model = "glm-4.7-flash"))
        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenThrow(RuntimeException("boom"))
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }

        service.generateForSource(source.id, userId)

        assertEquals(TopicExtractionState.FAILED, source.topicExtractionState)
        assertEquals("generation_failed", source.topicExtractionFailureReason)
        verify(sourceRepository).save(source)
    }

    private fun createActiveSource(userId: UUID): Source {
        return Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://example.com/topic-source",
            userId = userId
        ).apply {
            startExtraction()
            val content = Content.from("topic extraction content")
            completeExtraction(
                content,
                Metadata.from(
                    title = "Example",
                    author = "Author",
                    publishedDate = null,
                    platform = "web",
                    wordCount = content.wordCount,
                    aiFormatted = true,
                    extractionProvider = "jsoup"
                )
            )
            markTopicExtractionPending()
        }
    }
}
