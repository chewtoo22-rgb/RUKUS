package com.ruckus.agent.core

/**
 * Revalidates durable COMPLETE evidence against the planner and completion semantics currently
 * shipping in the app. A persisted completion checkpoint is proof of a specific admitted plan
 * and terminal observation, not permission to display VERIFIED COMPLETE forever if parsing,
 * admission, or completion-proof rules later change.
 */
object CompletedTaskEvidencePolicy {
    fun isStillValid(session: PersistedTaskSession): Boolean {
        if (session.status != AgentTaskState.Status.COMPLETE) return false
        if (!PersistedSessionIntegrityPolicy.evaluate(session).allowed) return false

        val plan = CommandPlanner.plan(session.request)
        if (plan.actions.isEmpty() || plan.rejectedParts.isNotEmpty()) return false
        if (plan.actions.size != session.totalSteps) return false
        if (PlanFingerprint.of(plan) != session.planFingerprint) return false
        if (session.currentStep != plan.actions.size) return false

        // Durable completion evidence must still describe the exact terminal action of the
        // plan produced by the currently shipping planner. The checkpoint/evidence digests
        // prove stored bytes were not corrupted; this check proves those bytes still describe
        // the semantic endpoint of the admitted plan rather than some other terminal action.
        if (session.lastAction != plan.actions.last().toString()) return false

        // Completion semantics can tighten independently of parsing/admission. Re-run the
        // current completion gate against the persisted final observation so a checkpoint that
        // was once accepted cannot remain VERIFIED COMPLETE after the app learns to require
        // stronger terminal evidence.
        if (!TaskCompletionGate.evaluate(plan, session.currentStep, session.lastScreenSummary).ok) {
            return false
        }

        return true
    }
}
