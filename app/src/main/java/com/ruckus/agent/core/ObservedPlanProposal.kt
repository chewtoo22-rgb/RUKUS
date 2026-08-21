package com.ruckus.agent.core

import java.security.MessageDigest

/**
 * Binds a bounded plan proposal to the exact UI observation it was derived from.
 *
 * A future reasoning planner may propose typed AgentActions, but those actions must not be
 * executed against a different screen than the one the planner inspected. This gate gives the
 * inspect -> plan boundary deterministic freshness, proposal-integrity, and short-lived lease
 * checks before execution.
 */
data class ObservedPlanProposal(
    val goal: String,
    val actions: List<AgentAction>,
    val observationFingerprint: String,
    val planFingerprint: String,
    val issuedAtEpochMs: Long,
    val proposalFingerprint: String,
) {
    companion object {
        const val MAX_GOAL_LENGTH = 500
        const val MAX_PROPOSAL_AGE_MS = 10_000L

        fun create(
            goal: String,
            actions: List<AgentAction>,
            observation: String?,
            nowEpochMs: Long = System.currentTimeMillis(),
        ): Result<ObservedPlanProposal> {
            val cleanGoal = goal.trim()
            if (cleanGoal.isEmpty()) return Result.failure(IllegalArgumentException("Goal is blank"))
            if (cleanGoal.length > MAX_GOAL_LENGTH) {
                return Result.failure(IllegalArgumentException("Goal exceeds $MAX_GOAL_LENGTH characters"))
            }
            if (nowEpochMs < 0L) return Result.failure(IllegalArgumentException("Proposal timestamp is invalid"))

            val normalizedObservation = normalizeObservation(observation)
                ?: return Result.failure(IllegalArgumentException("Planner observation is missing or not package-aware"))
            val admittedActions = actions.toList()
            val admission = PlanAdmissionPolicy.evaluate(admittedActions)
            if (!admission.allowed) return Result.failure(IllegalArgumentException(admission.reason))
            val reasoningAdmission = ReasoningPlanPolicy.evaluate(admittedActions)
            if (!reasoningAdmission.allowed) {
                return Result.failure(IllegalArgumentException(reasoningAdmission.reason))
            }
            val plan = CommandPlanner.Plan(admittedActions, emptyList())
            val observationFingerprint = fingerprint(normalizedObservation)
            val planFingerprint = PlanFingerprint.of(plan)
            val proposalFingerprint = proposalFingerprint(
                cleanGoal,
                observationFingerprint,
                planFingerprint,
                nowEpochMs,
            )

            return Result.success(
                ObservedPlanProposal(
                    goal = cleanGoal,
                    actions = admittedActions,
                    observationFingerprint = observationFingerprint,
                    planFingerprint = planFingerprint,
                    issuedAtEpochMs = nowEpochMs,
                    proposalFingerprint = proposalFingerprint,
                )
            )
        }

        internal fun normalizeObservation(observation: String?): String? {
            val value = observation?.trim()?.replace("\r\n", "\n") ?: return null
            if (!value.startsWith("pkg=")) return null
            return value
        }

        internal fun fingerprint(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        internal fun proposalFingerprint(
            goal: String,
            observationFingerprint: String,
            planFingerprint: String,
            issuedAtEpochMs: Long,
        ): String = fingerprint(
            listOf(goal, observationFingerprint, planFingerprint, issuedAtEpochMs.toString())
                .joinToString("\u001f")
        )
    }
}

object ObservedPlanFreshnessGate {
    data class Decision(val allowed: Boolean, val reason: String)

    fun evaluate(
        proposal: ObservedPlanProposal,
        currentObservation: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Decision {
        if (nowEpochMs < proposal.issuedAtEpochMs) {
            return Decision(false, "Planner proposal timestamp is in the future; discard and replan")
        }
        if (nowEpochMs - proposal.issuedAtEpochMs > ObservedPlanProposal.MAX_PROPOSAL_AGE_MS) {
            return Decision(false, "Planner proposal lease expired; re-inspection and replanning required")
        }

        val expectedProposalFingerprint = ObservedPlanProposal.proposalFingerprint(
            proposal.goal,
            proposal.observationFingerprint,
            proposal.planFingerprint,
            proposal.issuedAtEpochMs,
        )
        if (expectedProposalFingerprint != proposal.proposalFingerprint) {
            return Decision(false, "Planner proposal metadata changed after admission; discard and replan")
        }

        val normalized = ObservedPlanProposal.normalizeObservation(currentObservation)
            ?: return Decision(false, "Current UI observation is missing or not package-aware")
        val currentFingerprint = ObservedPlanProposal.fingerprint(normalized)
        if (currentFingerprint != proposal.observationFingerprint) {
            return Decision(false, "UI changed after planning; re-inspection and replanning required")
        }

        val admission = PlanAdmissionPolicy.evaluate(proposal.actions)
        if (!admission.allowed) {
            return Decision(false, "Proposed plan no longer passes admission: ${admission.reason}")
        }
        val reasoningAdmission = ReasoningPlanPolicy.evaluate(proposal.actions)
        if (!reasoningAdmission.allowed) {
            return Decision(false, "Proposed plan no longer passes reasoning admission: ${reasoningAdmission.reason}")
        }
        val currentPlanFingerprint = PlanFingerprint.of(CommandPlanner.Plan(proposal.actions, emptyList()))
        if (currentPlanFingerprint != proposal.planFingerprint) {
            return Decision(false, "Proposed actions changed after admission; discard and replan")
        }

        return Decision(true, "Plan is intact, short-lived, and bound to the current UI observation")
    }
}
