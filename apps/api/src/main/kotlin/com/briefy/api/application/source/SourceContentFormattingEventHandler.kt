package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.event.SourceContentFormattingRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SourceContentFormattingEventHandler(
    private val sourceContentFormatterService: SourceContentFormatterService
) {
    private val logger = LoggerFactory.getLogger(SourceContentFormattingEventHandler::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onFormattingRequested(event: SourceContentFormattingRequestedEvent) {
        logger.info(
            "[event] SourceContentFormattingRequested sourceId={} userId={} extractorId={} occurredAt={}",
            event.sourceId,
            event.userId,
            event.extractorId,
            event.occurredAt
        )
        sourceContentFormatterService.formatSourceContent(
            sourceId = event.sourceId,
            userId = event.userId,
            extractorId = event.extractorId
        )
    }
}
