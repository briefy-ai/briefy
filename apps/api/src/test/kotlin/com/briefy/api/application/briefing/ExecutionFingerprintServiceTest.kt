package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.Briefing
import com.briefy.api.domain.knowledgegraph.briefing.BriefingEnrichmentIntent
import com.briefy.api.domain.knowledgegraph.briefing.BriefingPlanStep
import com.briefy.api.domain.knowledgegraph.briefing.BriefingPlanStepStatus
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ExecutionFingerprintServiceTest {
    private val service = ExecutionFingerprintService(ObjectMapper())

    @Test
    fun `compute is deterministic across source and plan input ordering`() {
        val now = Instant.parse("2026-02-28T00:00:00Z")
        val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val briefing = Briefing.create(
            id = UUID.fromString("00000000-0000-0000-0000-000000000100"),
            userId = userId,
            enrichmentIntent = BriefingEnrichmentIntent.DEEP_DIVE,
            now = now
        )

        val sourceA = source(
            id = UUID.fromString("00000000-0000-0000-0000-000000000201"),
            userId = userId,
            title = "Alpha",
            text = "alpha evidence"
        )
        val sourceB = source(
            id = UUID.fromString("00000000-0000-0000-0000-000000000202"),
            userId = userId,
            title = "Beta",
            text = "beta evidence"
        )

        val step1 = planStep(
            id = UUID.fromString("00000000-0000-0000-0000-000000000301"),
            briefingId = briefing.id,
            order = 1,
            personaName = "Analyst",
            task = "Analyze"
        )
        val step2 = planStep(
            id = UUID.fromString("00000000-0000-0000-0000-000000000302"),
            briefingId = briefing.id,
            order = 2,
            personaName = "Skeptic",
            task = "Challenge assumptions"
        )

        val fingerprintA = service.compute(
            briefing = briefing,
            orderedSources = listOf(sourceB, sourceA),
            orderedPlanSteps = listOf(step2, step1)
        )
        val fingerprintB = service.compute(
            briefing = briefing,
            orderedSources = listOf(sourceA, sourceB),
            orderedPlanSteps = listOf(step1, step2)
        )

        assertEquals(fingerprintA, fingerprintB)
    }

    private fun source(
        id: UUID,
        userId: UUID,
        title: String,
        text: String
    ): Source {
        return Source(
            id = id,
            url = Url.from("https://example.com/$id"),
            status = SourceStatus.ACTIVE,
            content = Content.from(text),
            metadata = Metadata.from(
                title = title,
                author = "author",
                publishedDate = Instant.parse("2026-02-01T00:00:00Z"),
                platform = "web",
                wordCount = Content.countWords(text),
                aiFormatted = true,
                extractionProvider = "test"
            ),
            userId = userId,
            createdAt = Instant.parse("2026-02-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-01T00:00:00Z")
        )
    }

    private fun planStep(
        id: UUID,
        briefingId: UUID,
        order: Int,
        personaName: String,
        task: String
    ): BriefingPlanStep {
        return BriefingPlanStep(
            id = id,
            briefingId = briefingId,
            personaId = null,
            personaName = personaName,
            stepOrder = order,
            task = task,
            status = BriefingPlanStepStatus.PLANNED,
            createdAt = Instant.parse("2026-02-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-01T00:00:00Z")
        )
    }
}
