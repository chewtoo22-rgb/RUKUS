package com.ruckus.agent.core

import java.security.MessageDigest

/**
 * Binds a bounded plan proposal to the exact UI observation it was derived from.
 *
 * A future reasoning planner may propose typed AgentActions, but those actions must not be
 * executed against a different screen than the one the planner inspected. This gate gives the
 * inspect -> plan boundary deterministic freshness and proposal-integrity checks before execution.
 */
data class ObservedPlanProposal(
    val goal: String,
    val actions: List<AgentAction>,
    val observationFingerprint: String,
    val planFingerprint: String,
) {
    companion object {
        const val MAX_GOAL_LENGTH = 500

        fun create(goal: String, actions: List<AgentAction>, observation: String?): Result<ObservedPlanProposal> {
            val cleanGoal = goal.trim()
            if (cleanGoal.isEmpty()) return Result.failure(IllegalArgumentException("Goal is blank"))
            if (cleanGoal.length > MAX_GOAL_LENGTH) {
                return Result.failure(IllegalArgumentException("Goal exceeds $MAX_GOAL_LENGTH characters"))
            }
            val normalizedObservation = normalizeObservation(observation)
                ?: return Result.failure(IllegalArgumentException("Planner observation is missing or not package-aware"))
            val admittedActions = actions.toList()
            val admission = PlanAdmissionPolicy.evaluate(admittedActions)
            if (!admission.allowed) return Result.failure(IllegalArgumentException(admission.reason))
            val plan = CommandPlanner.Plan(admittedActions, emptyList())

            return Result.success(
                ObservedPlanProposal(
                    goal = cleanGoal,
                    actions = admittedActions,
                    observationFingerprint = fingerprint(normalizedObservation),
                    planFingerprint = PlanFingerprint.of(plan),
                )
            )
        }

        internal fun normalizeObservation(observation: String?): String? {
            val value = observation?.trim()?.replace("\r\n", "\n") ?: return null
            if (!value.startsWith("pkg=")) return null
            return value
        }

        internal fun fingerprint(observation: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(observation.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

object ObservedPlanFreshnessGate {
    data class Decision(val allowed: Boolean, val reason: String)

    fun evaluate(proposal: ObservedPlanProposal, currentObservation: String?): Decision {
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
        val currentPlanFingerprint = PlanFingerprint.of(CommandPlanner.Plan(proposal.actions, emptyList()))
        if (currentPlanFingerprint != proposal.planFingerprint) {
            return Decision(false, "Proposed actions changed after admission; discard and replan")
        }

        return Decision(true, "Plan is intact and bound to the current UI observation")
    }
}
