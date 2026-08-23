package com.ruckus.agent.core

/**
 * Single fail-closed integration boundary for future natural-language reasoning output.
 *
 * Model-produced text must first decode through ReasoningActionCodec and then immediately become
 * an observation-bound ObservedPlanProposal. Callers never receive an executable action list from
 * this gateway without also passing the exact observation, goal-intent, grounding, freshness, and
 * proposal-integrity admission path.
 */
object ReasoningPlanGateway {
    data class ProposalResult(
        val proposal: ObservedPlanProposal? = null,
        val error: String? = null,
    ) {
        val allowed: Boolean get() = proposal != null && error == null
    }

    fun propose(
        goal: String,
        observation: String?,
        encodedActions: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ProposalResult {
        val decoded = ReasoningActionCodec.decode(encodedActions)
        if (!decoded.allowed) {
            return ProposalResult(error = "Reasoning action decode rejected: ${decoded.error}")
        }

        return ObservedPlanProposal.create(
            goal = goal,
            actions = decoded.actions,
            observation = observation,
            nowEpochMs = nowEpochMs,
        ).fold(
            onSuccess = { ProposalResult(proposal = it) },
            onFailure = {
                ProposalResult(
                    error = "Observed reasoning proposal rejected: ${it.message ?: "unknown admission failure"}"
                )
            },
        )
    }
}
