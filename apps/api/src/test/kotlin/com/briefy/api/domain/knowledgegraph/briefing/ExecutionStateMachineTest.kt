package com.briefy.api.domain.knowledgegraph.briefing

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExecutionStateMachineTest {

    @Test
    fun `briefing run transitions follow locked contract`() {
        assertTrue(BriefingRunStatus.QUEUED.canTransitionTo(BriefingRunStatus.RUNNING))
        assertTrue(BriefingRunStatus.RUNNING.canTransitionTo(BriefingRunStatus.CANCELLING))
        assertTrue(BriefingRunStatus.RUNNING.canTransitionTo(BriefingRunStatus.SUCCEEDED))
        assertTrue(BriefingRunStatus.RUNNING.canTransitionTo(BriefingRunStatus.FAILED))
        assertTrue(BriefingRunStatus.CANCELLING.canTransitionTo(BriefingRunStatus.CANCELLED))

        assertFalse(BriefingRunStatus.QUEUED.canTransitionTo(BriefingRunStatus.FAILED))
        assertFalse(BriefingRunStatus.SUCCEEDED.canTransitionTo(BriefingRunStatus.RUNNING))
        assertFalse(BriefingRunStatus.FAILED.canTransitionTo(BriefingRunStatus.RUNNING))
        assertFalse(BriefingRunStatus.CANCELLED.canTransitionTo(BriefingRunStatus.RUNNING))
    }

    @Test
    fun `subagent transitions include skipped_no_output and retry_wait paths`() {
        assertTrue(SubagentRunStatus.PENDING.canTransitionTo(SubagentRunStatus.RUNNING))
        assertTrue(SubagentRunStatus.PENDING.canTransitionTo(SubagentRunStatus.CANCELLED))
        assertTrue(SubagentRunStatus.RUNNING.canTransitionTo(SubagentRunStatus.SUCCEEDED))
        assertTrue(SubagentRunStatus.RUNNING.canTransitionTo(SubagentRunStatus.SKIPPED_NO_OUTPUT))
        assertTrue(SubagentRunStatus.RUNNING.canTransitionTo(SubagentRunStatus.SKIPPED))
        assertTrue(SubagentRunStatus.RUNNING.canTransitionTo(SubagentRunStatus.RETRY_WAIT))
        assertTrue(SubagentRunStatus.RUNNING.canTransitionTo(SubagentRunStatus.FAILED))
        assertTrue(SubagentRunStatus.RUNNING.canTransitionTo(SubagentRunStatus.CANCELLED))
        assertTrue(SubagentRunStatus.RETRY_WAIT.canTransitionTo(SubagentRunStatus.RUNNING))
        assertTrue(SubagentRunStatus.RETRY_WAIT.canTransitionTo(SubagentRunStatus.CANCELLED))

        assertFalse(SubagentRunStatus.PENDING.canTransitionTo(SubagentRunStatus.SUCCEEDED))
        assertFalse(SubagentRunStatus.SUCCEEDED.canTransitionTo(SubagentRunStatus.RUNNING))
        assertFalse(SubagentRunStatus.SKIPPED_NO_OUTPUT.canTransitionTo(SubagentRunStatus.RUNNING))
    }

    @Test
    fun `synthesis transitions follow gate and terminal rules`() {
        assertTrue(SynthesisRunStatus.NOT_STARTED.canTransitionTo(SynthesisRunStatus.RUNNING))
        assertTrue(SynthesisRunStatus.NOT_STARTED.canTransitionTo(SynthesisRunStatus.SKIPPED))
        assertTrue(SynthesisRunStatus.NOT_STARTED.canTransitionTo(SynthesisRunStatus.CANCELLED))
        assertTrue(SynthesisRunStatus.RUNNING.canTransitionTo(SynthesisRunStatus.SUCCEEDED))
        assertTrue(SynthesisRunStatus.RUNNING.canTransitionTo(SynthesisRunStatus.FAILED))
        assertTrue(SynthesisRunStatus.RUNNING.canTransitionTo(SynthesisRunStatus.CANCELLED))

        assertFalse(SynthesisRunStatus.NOT_STARTED.canTransitionTo(SynthesisRunStatus.SUCCEEDED))
        assertFalse(SynthesisRunStatus.SUCCEEDED.canTransitionTo(SynthesisRunStatus.RUNNING))
        assertFalse(SynthesisRunStatus.SKIPPED.canTransitionTo(SynthesisRunStatus.RUNNING))
    }
}
