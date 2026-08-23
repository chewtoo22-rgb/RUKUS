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

    /**
     * A deliberately narrow handoff produced only after the proposal has been revalidated against
     * the current UI observation immediately before execution.
     *
     * The grant copies the canonical admitted actions so callers cannot mutate the proposal's list
     * after freshness/integrity validation and then execute a different plan.
     */
    data class ExecutionGrant internal constructor(
        val goal: String,
        val actions: List<AgentAction>,
        val proposalFingerprint: String,
        val observationFingerprint: String,
        val grantedAtEpochMs: Long,
    )

    data class ExecutionResult(
        val grant: ExecutionGrant? = null,
        val error: String? = null,
    ) {
        val allowed: Boolean get() = grant != null && error == null
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

    /**
     * Mandatory execution handoff for reasoning proposals.
     *
     * This reruns the full ObservedPlanFreshnessGate immediately before any autonomous action list
     * is exposed for execution. Stale UI, expired leases, changed actions, changed goal metadata,
     * failed grounding, or any other proposal-integrity regression fails closed and requires a new
     * inspection + proposal.
     */
    fun authorizeForExecution(
        proposal: ObservedPlanProposal,
        currentObservation: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ExecutionResult {
        val freshness = ObservedPlanFreshnessGate.evaluate(
            proposal = proposal,
            currentObservation = currentObservation,
            nowEpochMs = nowEpochMs,
        )
        if (!freshness.allowed) {
            return ExecutionResult(error = "Reasoning execution authorization rejected: ${freshness.reason}")
        }

        return ExecutionResult(
            grant = ExecutionGrant(
                goal = proposal.goal,
                actions = proposal.actions.toList(),
                proposalFingerprint = proposal.proposalFingerprint,
                observationFingerprint = proposal.observationFingerprint,
                grantedAtEpochMs = nowEpochMs,
            )
        )
    }
}
