package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.SourceType
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class FreshnessPolicy {
    fun ttlSeconds(sourceType: SourceType): Long = sourceType.ttlSeconds()

    fun computeExpiresAt(sourceType: SourceType, now: Instant): Instant {
        return now.plusSeconds(ttlSeconds(sourceType))
    }

    fun isFresh(expiresAt: Instant, now: Instant): Boolean = !expiresAt.isBefore(now)
}
