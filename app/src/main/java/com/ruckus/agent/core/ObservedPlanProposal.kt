package com.ruckus.agent.core

import java.security.MessageDigest

/**
 * Binds a bounded plan proposal to the exact UI observation it was derived from.
 *
 * A future reasoning planner may propose typed AgentActions, but those actions must not be
 * executed against a different screen than the one the planner inspected. This gate gives the
 * inspect -> plan boundary a deterministic freshness check before execution begins.
 */
data class ObservedPlanProposal(
    val goal: String,
    val actions: List<AgentAction>,
    val observationFingerprint: String,
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
            val admission = PlanAdmissionPolicy.evaluate(actions)
            if (!admission.allowed) return Result.failure(IllegalArgumentException(admission.reason))

            return Result.success(
                ObservedPlanProposal(
                    goal = cleanGoal,
                    actions = actions.toList(),
                    observationFingerprint = fingerprint(normalizedObservation),
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
        return Decision(true, "Plan is bound to the current UI observation")
    }
}
