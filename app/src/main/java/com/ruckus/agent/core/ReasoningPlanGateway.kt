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
    const val MAX_EXECUTION_GRANT_AGE_MS = 2_000L

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
        val planFingerprint: String,
        val grantedAtEpochMs: Long,
        val grantFingerprint: String,
    )

    data class ExecutionResult(
        val grant: ExecutionGrant? = null,
        val error: String? = null,
    ) {
        val allowed: Boolean get() = grant != null && error == null
    }

    /**
     * Final dispatch envelope. Only actions that pass the whole-plan safety preflight may reach
     * this envelope. Confirmation-gated reasoning actions are deliberately not exposed here; they
     * must continue through the executor's persisted, action-bound confirmation path.
     */
    data class DispatchGrant internal constructor(
        val goal: String,
        val actions: List<AgentAction>,
        val proposalFingerprint: String,
        val observationFingerprint: String,
        val planFingerprint: String,
        val authorizedAtEpochMs: Long,
    )

    data class DispatchResult(
        val grant: DispatchGrant? = null,
        val error: String? = null,
        val needsConfirmation: Boolean = false,
        val confirmationActionFingerprint: String? = null,
    ) {
        val allowed: Boolean get() = grant != null && error == null && !needsConfirmation
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

        val grantFingerprint = executionGrantFingerprint(
            goal = proposal.goal,
            proposalFingerprint = proposal.proposalFingerprint,
            observationFingerprint = proposal.observationFingerprint,
            planFingerprint = proposal.planFingerprint,
            grantedAtEpochMs = nowEpochMs,
        )
        return ExecutionResult(
            grant = ExecutionGrant(
                goal = proposal.goal,
                actions = proposal.actions.toList(),
                proposalFingerprint = proposal.proposalFingerprint,
                observationFingerprint = proposal.observationFingerprint,
                planFingerprint = proposal.planFingerprint,
                grantedAtEpochMs = nowEpochMs,
                grantFingerprint = grantFingerprint,
            )
        )
    }

    /**
     * Final just-in-time dispatch gate.
     *
     * Execution authorization is intentionally short-lived. Before actions are handed to the
     * controller, the exact current observation, plan identity, goal binding, grounding, grant
     * metadata, and whole-plan safety classification are checked again. A held, copied, mutated,
     * future-dated, stale, blocked, or confirmation-gated grant fails closed and forces either a
     * fresh inspect -> propose -> authorize cycle or the executor's explicit confirmation path.
     */
    fun authorizeForDispatch(
        executionGrant: ExecutionGrant,
        currentObservation: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DispatchResult {
        if (nowEpochMs < executionGrant.grantedAtEpochMs) {
            return DispatchResult(error = "Reasoning dispatch rejected: execution grant timestamp is in the future; re-inspect and replan")
        }
        if (nowEpochMs - executionGrant.grantedAtEpochMs > MAX_EXECUTION_GRANT_AGE_MS) {
            return DispatchResult(error = "Reasoning dispatch rejected: execution grant expired; re-inspect and replan")
        }

        val expectedGrantFingerprint = executionGrantFingerprint(
            goal = executionGrant.goal,
            proposalFingerprint = executionGrant.proposalFingerprint,
            observationFingerprint = executionGrant.observationFingerprint,
            planFingerprint = executionGrant.planFingerprint,
            grantedAtEpochMs = executionGrant.grantedAtEpochMs,
        )
        if (expectedGrantFingerprint != executionGrant.grantFingerprint) {
            return DispatchResult(error = "Reasoning dispatch rejected: execution grant metadata changed after authorization")
        }

        val normalized = ObservedPlanProposal.normalizeObservation(currentObservation)
            ?: return DispatchResult(error = "Reasoning dispatch rejected: current UI observation is missing or not package-aware")
        val currentObservationFingerprint = ObservedPlanProposal.fingerprint(normalized)
        if (currentObservationFingerprint != executionGrant.observationFingerprint) {
            return DispatchResult(error = "Reasoning dispatch rejected: UI changed after execution authorization; re-inspect and replan")
        }

        val actions = executionGrant.actions.toList()
        val admission = PlanAdmissionPolicy.evaluate(actions)
        if (!admission.allowed) {
            return DispatchResult(error = "Reasoning dispatch rejected: plan no longer passes admission: ${admission.reason}")
        }
        val reasoningAdmission = ReasoningPlanPolicy.evaluate(actions)
        if (!reasoningAdmission.allowed) {
            return DispatchResult(error = "Reasoning dispatch rejected: plan no longer passes reasoning admission: ${reasoningAdmission.reason}")
        }
        val intentBinding = ReasoningIntentBindingPolicy.evaluate(executionGrant.goal, actions)
        if (!intentBinding.allowed) {
            return DispatchResult(error = "Reasoning dispatch rejected: plan no longer matches explicit goal intent: ${intentBinding.reason}")
        }
        val grounding = ReasoningGroundingPolicy.evaluate(actions, normalized)
        if (!grounding.allowed) {
            return DispatchResult(error = "Reasoning dispatch rejected: plan no longer passes UI grounding: ${grounding.reason}")
        }
        val currentPlanFingerprint = PlanFingerprint.of(CommandPlanner.Plan(actions, emptyList()))
        if (currentPlanFingerprint != executionGrant.planFingerprint) {
            return DispatchResult(error = "Reasoning dispatch rejected: actions changed after execution authorization")
        }

        // Defense-in-depth at the final controller boundary. Reasoning dispatch may expose only
        // wholly SAFE plans. High-impact actions must flow through RuckusExecutor so its persisted,
        // action-bound approval checkpoint is the only way to authorize a confirmation-required
        // side effect; a caller cannot bypass that contract by presenting a computable fingerprint.
        val safety = PlanSafetyPreflight.evaluate(actions)
        if (!safety.allowed) {
            if (safety.needsConfirmation && safety.action != null) {
                return DispatchResult(
                    error = "Reasoning dispatch requires executor confirmation: ${safety.reason}",
                    needsConfirmation = true,
                    confirmationActionFingerprint = PlanSafetyPreflight.approvalFingerprint(safety.action),
                )
            }
            return DispatchResult(error = "Reasoning dispatch rejected by safety preflight: ${safety.reason}")
        }

        return DispatchResult(
            grant = DispatchGrant(
                goal = executionGrant.goal,
                actions = actions,
                proposalFingerprint = executionGrant.proposalFingerprint,
                observationFingerprint = executionGrant.observationFingerprint,
                planFingerprint = executionGrant.planFingerprint,
                authorizedAtEpochMs = nowEpochMs,
            )
        )
    }

    internal fun executionGrantFingerprint(
        goal: String,
        proposalFingerprint: String,
        observationFingerprint: String,
        planFingerprint: String,
        grantedAtEpochMs: Long,
    ): String = ObservedPlanProposal.fingerprint(
        listOf(
            goal,
            proposalFingerprint,
            observationFingerprint,
            planFingerprint,
            grantedAtEpochMs.toString(),
        ).joinToString("\u001f")
    )
}
