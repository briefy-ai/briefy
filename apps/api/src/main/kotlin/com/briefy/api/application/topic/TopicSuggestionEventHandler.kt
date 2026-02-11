package com.briefy.api.application.topic

import com.briefy.api.domain.knowledgegraph.source.event.SourceActivatedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class TopicSuggestionEventHandler(
    private val topicSuggestionService: TopicSuggestionService
) {
    private val logger = LoggerFactory.getLogger(TopicSuggestionEventHandler::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSourceActivated(event: SourceActivatedEvent) {
        logger.info(
            "[event] SourceActivated sourceId={} userId={} reason={}",
            event.sourceId,
            event.userId,
            event.activationReason
        )
        topicSuggestionService.generateForSource(
            sourceId = event.sourceId,
            userId = event.userId
        )
    }
}
