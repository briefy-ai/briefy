package com.briefy.api.application.enrichment

import com.briefy.api.domain.knowledgegraph.source.event.SourceContentFinalizedEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class SourceContentFinalizedEventHandlerTest {
    private val sourceEmbeddingService: SourceEmbeddingService = mock()

    private val handler = SourceContentFinalizedEventHandler(
        sourceEmbeddingService = sourceEmbeddingService
    )

    @Test
    fun `onSourceContentFinalized delegates embedding generation`() {
        val sourceId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        handler.onSourceContentFinalized(
            SourceContentFinalizedEvent(
                sourceId = sourceId,
                userId = userId
            )
        )

        verify(sourceEmbeddingService).generateForSource(sourceId, userId)
    }
}
