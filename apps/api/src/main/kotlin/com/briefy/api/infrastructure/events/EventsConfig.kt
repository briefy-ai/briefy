package com.briefy.api.infrastructure.events

import com.briefy.api.domain.knowledgegraph.source.event.SourceArchivedEvent
import com.briefy.api.domain.knowledgegraph.source.event.SourceContentFinalizedEvent
import com.briefy.api.domain.knowledgegraph.source.event.SourceRestoredEvent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Configuration
class EventsConfig {
    private val logger = LoggerFactory.getLogger(EventsConfig::class.java)

    @EventListener
    fun onSourceArchived(event: SourceArchivedEvent) {
        logger.info(
            "[event] SourceArchived sourceId={} userId={} occurredAt={}",
            event.sourceId,
            event.userId,
            event.occurredAt
        )
    }

    @EventListener
    fun onSourceRestored(event: SourceRestoredEvent) {
        logger.info(
            "[event] SourceRestored sourceId={} userId={} occurredAt={}",
            event.sourceId,
            event.userId,
            event.occurredAt
        )
    }

    @EventListener
    fun onSourceContentFinalized(event: SourceContentFinalizedEvent) {
        logger.info(
            "[event] SourceContentFinalized sourceId={} userId={} occurredAt={}",
            event.sourceId,
            event.userId,
            event.occurredAt
        )
    }
}
