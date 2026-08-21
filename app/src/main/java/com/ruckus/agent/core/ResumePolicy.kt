package com.ruckus.agent.core

data class ResumeDecision(
    val allowed: Boolean,
    val startStep: Int = 0,
    val reason: String
)

/**
 * Conservative checkpoint resume policy.
 * Completed/rejected sessions are never resumed. A persisted RUNNING checkpoint
 * resumes at the first unverified step; confirmation-gated work stays gated.
 * EXECUTING means the process may have died after dispatch, so the executor must
 * reconcile the observed outcome before deciding whether a replay is safe.
 */
object ResumePolicy {
    fun decide(session: PersistedTaskSession?, plan: CommandPlanner.Plan): ResumeDecision {
        if (session == null) return ResumeDecision(false, reason = "No saved task session")
        if (session.request.isBlank()) return ResumeDecision(false, reason = "Saved task has no request")
        if (plan.actions.isEmpty() || plan.rejectedParts.isNotEmpty()) return ResumeDecision(false, reason = "Saved request no longer produces a valid plan")
        if (session.totalSteps != plan.actions.size) return ResumeDecision(false, reason = "Saved plan shape changed")

        val expectedFingerprint = PlanFingerprint.of(plan)
        val savedFingerprint = session.planFingerprint
            ?: return ResumeDecision(false, reason = "Saved task predates plan-integrity checkpoints; start a fresh request")
        if (savedFingerprint != expectedFingerprint) return ResumeDecision(false, reason = "Saved plan semantics changed")

        if (session.status == AgentTaskState.Status.COMPLETE) return ResumeDecision(false, reason = "Task is already complete")
        if (session.status == AgentTaskState.Status.FAILED) return ResumeDecision(false, reason = "Failed tasks require a fresh user request")
        if (session.status == AgentTaskState.Status.IDLE) return ResumeDecision(false, reason = "No active task to resume")

        val step = session.currentStep.coerceIn(0, plan.actions.size)
        if (step >= plan.actions.size) return ResumeDecision(false, reason = "All planned steps are already checkpointed")

        return when (session.status) {
            AgentTaskState.Status.WAITING_CONFIRMATION -> ResumeDecision(true, step, "Resume at confirmation-gated step with exact saved plan")
            AgentTaskState.Status.EXECUTING -> ResumeDecision(true, step, "Reconcile ambiguous in-flight action before any replay")
            AgentTaskState.Status.RUNNING,
            AgentTaskState.Status.RECOVERING -> ResumeDecision(true, step, "Resume from first unverified checkpoint with exact saved plan")
            else -> ResumeDecision(false, reason = "Session is not resumable")
        }
    }
}
