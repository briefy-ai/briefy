package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.event.SourceNarrationRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SourceNarrationEventHandler(
    private val sourceNarrationService: SourceNarrationService
) {
    private val logger = LoggerFactory.getLogger(SourceNarrationEventHandler::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onNarrationRequested(event: SourceNarrationRequestedEvent) {
        logger.info(
            "[event] SourceNarrationRequested sourceId={} userId={} occurredAt={}",
            event.sourceId,
            event.userId,
            event.occurredAt
        )
        sourceNarrationService.processNarration(event.sourceId, event.userId)
    }
}
