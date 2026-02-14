package com.briefy.api.application.source

import org.springframework.stereotype.Component
import java.util.UUID

interface SourceDependencyChecker {
    fun hasBlockingDependencies(sourceId: UUID, userId: UUID): Boolean
}

@Component
class NoopSourceDependencyChecker : SourceDependencyChecker {
    override fun hasBlockingDependencies(sourceId: UUID, userId: UUID): Boolean {
        return false
    }
}
