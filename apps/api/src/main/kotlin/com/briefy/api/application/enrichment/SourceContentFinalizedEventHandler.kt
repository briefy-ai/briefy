package com.briefy.api.application.enrichment

import com.briefy.api.domain.knowledgegraph.source.event.SourceContentFinalizedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SourceContentFinalizedEventHandler(
    private val sourceEmbeddingService: SourceEmbeddingService
) {
    private val logger = LoggerFactory.getLogger(SourceContentFinalizedEventHandler::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSourceContentFinalized(event: SourceContentFinalizedEvent) {
        logger.info(
            "[event] SourceContentFinalized sourceId={} userId={} occurredAt={}",
            event.sourceId,
            event.userId,
            event.occurredAt
        )
        sourceEmbeddingService.generateForSource(
            sourceId = event.sourceId,
            userId = event.userId
        )
    }
}
