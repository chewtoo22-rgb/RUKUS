package com.ruckus.agent.core

/**
 * Single-call just-in-time handoff for an already admitted observation-bound reasoning proposal.
 *
 * This coordinator deliberately does not execute actions. It composes the existing freshness,
 * integrity, grounding, whole-plan safety, and controller-dispatch leases into one fail-closed
 * boundary so callers cannot accidentally skip an intermediate authorization step.
 *
 * Confirmation-gated actions remain outside this path and must continue through RuckusExecutor's
 * persisted, exact-action confirmation flow.
 */
object ReasoningDispatchCoordinator {
    data class Result(
        val actions: List<AgentAction> = emptyList(),
        val error: String? = null,
        val needsConfirmation: Boolean = false,
        val confirmationActionFingerprint: String? = null,
    ) {
        val allowed: Boolean get() = actions.isNotEmpty() && error == null && !needsConfirmation
    }

    fun prepare(
        proposal: ObservedPlanProposal,
        currentObservation: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result {
        // Re-apply the raw-goal admission policy at the final handoff. Observed proposals have
        // their own tighter goal bound, but they predate GoalAdmissionPolicy and may still be
        // constructed from input containing unsupported control characters. The execution
        // boundary must never expose controller actions for a goal the normal executor would
        // reject before parsing/persistence.
        val goalAdmission = GoalAdmissionPolicy.evaluate(proposal.goal)
        if (!goalAdmission.allowed || goalAdmission.normalizedGoal != proposal.goal) {
            return Result(error = goalAdmission.reason.ifBlank { "Reasoning goal failed execution admission" })
        }

        val execution = ReasoningPlanGateway.authorizeForExecution(
            proposal = proposal,
            currentObservation = currentObservation,
            nowEpochMs = nowEpochMs,
        )
        val executionGrant = execution.grant
            ?: return Result(error = execution.error ?: "Reasoning execution authorization failed")

        val dispatch = ReasoningPlanGateway.authorizeForDispatch(
            executionGrant = executionGrant,
            currentObservation = currentObservation,
            nowEpochMs = nowEpochMs,
        )
        if (dispatch.needsConfirmation) {
            return Result(
                error = dispatch.error ?: "Reasoning dispatch requires executor confirmation",
                needsConfirmation = true,
                confirmationActionFingerprint = dispatch.confirmationActionFingerprint,
            )
        }
        val dispatchGrant = dispatch.grant
            ?: return Result(error = dispatch.error ?: "Reasoning dispatch authorization failed")

        val controller = ReasoningPlanGateway.authorizeControllerDispatch(
            dispatchGrant = dispatchGrant,
            currentObservation = currentObservation,
            nowEpochMs = nowEpochMs,
        )
        if (!controller.allowed) {
            return Result(error = controller.error ?: "Controller dispatch authorization failed")
        }

        return Result(actions = controller.actions.toList())
    }
}
