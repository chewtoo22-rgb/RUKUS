package com.ruckus.agent.core

/**
 * Revalidates durable COMPLETE evidence against the planner currently shipping in the app.
 * A persisted completion checkpoint is proof of a specific admitted plan, not permission to
 * display VERIFIED COMPLETE forever if parsing/admission semantics later change.
 */
object CompletedTaskEvidencePolicy {
    fun isStillValid(session: PersistedTaskSession): Boolean {
        if (session.status != AgentTaskState.Status.COMPLETE) return false
        if (!PersistedSessionIntegrityPolicy.evaluate(session).allowed) return false

        val plan = CommandPlanner.plan(session.request)
        if (plan.actions.isEmpty() || plan.rejectedParts.isNotEmpty()) return false
        if (plan.actions.size != session.totalSteps) return false
        if (PlanFingerprint.of(plan) != session.planFingerprint) return false
        return session.currentStep == plan.actions.size
    }
}
