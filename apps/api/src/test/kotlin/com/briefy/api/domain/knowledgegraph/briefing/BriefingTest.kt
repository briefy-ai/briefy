package com.briefy.api.domain.knowledgegraph.briefing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class BriefingTest {

    @Test
    fun `create initializes briefing in plan pending approval`() {
        val briefing = Briefing.create(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            enrichmentIntent = BriefingEnrichmentIntent.DEEP_DIVE
        )

        assertEquals(BriefingStatus.PLAN_PENDING_APPROVAL, briefing.status)
        assertNotNull(briefing.plannedAt)
        assertNull(briefing.contentMarkdown)
    }

    @Test
    fun `approve transitions from plan pending approval to approved`() {
        val briefing = createBriefing()

        briefing.approve()

        assertEquals(BriefingStatus.APPROVED, briefing.status)
        assertNotNull(briefing.approvedAt)
    }

    @Test
    fun `startGeneration transitions from approved to generating`() {
        val briefing = createBriefing().apply { approve() }

        briefing.startGeneration()

        assertEquals(BriefingStatus.GENERATING, briefing.status)
        assertNotNull(briefing.generationStartedAt)
    }

    @Test
    fun `completeGeneration transitions from generating to ready`() {
        val briefing = createBriefing().apply {
            approve()
            startGeneration()
        }

        briefing.completeGeneration(
            markdown = "# Briefing",
            citationsJson = "[]",
            conflictHighlightsJson = null
        )

        assertEquals(BriefingStatus.READY, briefing.status)
        assertEquals("# Briefing", briefing.contentMarkdown)
        assertEquals("[]", briefing.citationsJson)
        assertNotNull(briefing.generationCompletedAt)
        assertNull(briefing.errorJson)
    }

    @Test
    fun `failGeneration transitions from generating to failed`() {
        val briefing = createBriefing().apply {
            approve()
            startGeneration()
        }

        briefing.failGeneration("{\"code\":\"generation_failed\"}")

        assertEquals(BriefingStatus.FAILED, briefing.status)
        assertNotNull(briefing.failedAt)
        assertNotNull(briefing.errorJson)
    }

    @Test
    fun `resetForRetry transitions from failed to plan pending approval and clears output`() {
        val briefing = createBriefing().apply {
            approve()
            startGeneration()
            failGeneration("{\"code\":\"generation_failed\"}")
        }

        briefing.resetForRetry()

        assertEquals(BriefingStatus.PLAN_PENDING_APPROVAL, briefing.status)
        assertNull(briefing.contentMarkdown)
        assertNull(briefing.citationsJson)
        assertNull(briefing.conflictHighlightsJson)
        assertNull(briefing.errorJson)
        assertNull(briefing.approvedAt)
        assertNull(briefing.generationStartedAt)
        assertNull(briefing.generationCompletedAt)
        assertNull(briefing.failedAt)
        assertNotNull(briefing.plannedAt)
    }

    @Test
    fun `invalid transition from plan pending approval to generating throws`() {
        val briefing = createBriefing()

        assertThrows<IllegalArgumentException> {
            briefing.startGeneration()
        }
    }

    @Test
    fun `invalid transition from ready to approved throws`() {
        val briefing = createBriefing().apply {
            approve()
            startGeneration()
            completeGeneration("# done", "[]", null)
        }

        assertThrows<IllegalArgumentException> {
            briefing.approve()
        }
    }

    private fun createBriefing(): Briefing {
        return Briefing.create(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            enrichmentIntent = BriefingEnrichmentIntent.DEEP_DIVE
        )
    }
}
