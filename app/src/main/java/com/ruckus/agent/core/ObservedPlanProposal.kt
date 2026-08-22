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
            val intentBinding = ReasoningIntentBindingPolicy.evaluate(cleanGoal, admittedActions)
            if (!intentBinding.allowed) {
                return Result.failure(IllegalArgumentException(intentBinding.reason))
            }
            val grounding = ReasoningGroundingPolicy.evaluate(admittedActions, normalizedObservation)
            if (!grounding.allowed) {
                return Result.failure(IllegalArgumentException(grounding.reason))
            }

            // Resolve human-readable app labels to the exact package proven by the inspected
            // launchable-app inventory. This removes a late runtime name lookup from autonomous
            // execution, so the admitted proposal cannot drift to a different similarly named app.
            val canonicalActions = canonicalizeGroundedActions(admittedActions, normalizedObservation)
            val canonicalAdmission = PlanAdmissionPolicy.evaluate(canonicalActions)
            if (!canonicalAdmission.allowed) {
                return Result.failure(IllegalArgumentException(canonicalAdmission.reason))
            }
            val canonicalReasoningAdmission = ReasoningPlanPolicy.evaluate(canonicalActions)
            if (!canonicalReasoningAdmission.allowed) {
                return Result.failure(IllegalArgumentException(canonicalReasoningAdmission.reason))
            }
            val canonicalIntentBinding = ReasoningIntentBindingPolicy.evaluate(cleanGoal, canonicalActions)
            if (!canonicalIntentBinding.allowed) {
                return Result.failure(IllegalArgumentException(canonicalIntentBinding.reason))
            }
            val canonicalGrounding = ReasoningGroundingPolicy.evaluate(canonicalActions, normalizedObservation)
            if (!canonicalGrounding.allowed) {
                return Result.failure(IllegalArgumentException(canonicalGrounding.reason))
            }

            val plan = CommandPlanner.Plan(canonicalActions, emptyList())
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
                    actions = canonicalActions,
                    observationFingerprint = observationFingerprint,
                    planFingerprint = planFingerprint,
                    issuedAtEpochMs = nowEpochMs,
                    proposalFingerprint = proposalFingerprint,
                )
            )
        }

        internal fun canonicalizeGroundedActions(
            actions: List<AgentAction>,
            normalizedObservation: String,
        ): List<AgentAction> {
            val launchableLabels = ReasoningGroundingPolicy.launchableLabelPackages(normalizedObservation)
            return actions.map { action ->
                if (action is AgentAction.OpenAppByName) {
                    val label = ReasoningGroundingPolicy.normalizeLabel(action.appName)
                    val packages = launchableLabels[label].orEmpty()
                    if (packages.size == 1) AgentAction.OpenApp(packages.single()) else action
                } else {
                    action
                }
            }
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

/**
 * Deterministically binds device-wide setting mutations to explicit user intent in the goal.
 *
 * Brightness and media-volume changes do not have a UI target that ReasoningGroundingPolicy can
 * prove. A reasoning planner therefore may not introduce or alter them as an incidental step. The
 * goal must explicitly name the setting and the exact requested percentage. A few unambiguous
 * natural-language aliases (mute/max/full/minimum) are mapped to their deterministic endpoints.
 */
object ReasoningIntentBindingPolicy {
    data class Decision(val allowed: Boolean, val reason: String)

    private const val MAX_SETTING_NUMBER_DISTANCE = 32

    fun evaluate(goal: String, actions: List<AgentAction>): Decision {
        actions.forEachIndexed { index, action ->
            val allowed = when (action) {
                is AgentAction.SetBrightness -> requestedSettingValues(goal, "brightness")
                    .contains(action.percent)
                is AgentAction.SetMediaVolume -> requestedVolumeValues(goal).contains(action.percent)
                else -> true
            }

            if (!allowed) {
                val setting = when (action) {
                    is AgentAction.SetBrightness -> "brightness"
                    is AgentAction.SetMediaVolume -> "media volume"
                    else -> "setting"
                }
                return Decision(
                    false,
                    "Step ${index + 1}: autonomous $setting changes require the goal to explicitly request the exact target value",
                )
            }
        }

        return Decision(true, "Device-wide setting mutations are explicitly bound to the user goal")
    }

    internal fun requestedSettingValues(goal: String, keyword: String): Set<Int> {
        val normalized = goal.lowercase()
        val values = mutableSetOf<Int>()
        val escapedKeyword = Regex.escape(keyword.lowercase())
        val after = Regex("\\b$escapedKeyword\\b[^0-9]{0,$MAX_SETTING_NUMBER_DISTANCE}(\\d{1,3})\\s*(?:%|percent\\b)?")
        val before = Regex("(\\d{1,3})\\s*(?:%|percent\\b)?[^a-z0-9]{0,$MAX_SETTING_NUMBER_DISTANCE}\\b$escapedKeyword\\b")

        after.findAll(normalized).forEach { match ->
            match.groupValues[1].toIntOrNull()?.takeIf { it in 0..100 }?.let(values::add)
        }
        before.findAll(normalized).forEach { match ->
            match.groupValues[1].toIntOrNull()?.takeIf { it in 0..100 }?.let(values::add)
        }

        if (keyword == "brightness") {
            if (Regex("\\b(?:full|maximum|max)\\s+brightness\\b|\\bbrightness\\s+(?:full|maximum|max)\\b").containsMatchIn(normalized)) values += 100
            if (Regex("\\b(?:minimum|min)\\s+brightness\\b|\\bbrightness\\s+(?:minimum|min)\\b").containsMatchIn(normalized)) values += 0
        }
        return values
    }

    internal fun requestedVolumeValues(goal: String): Set<Int> {
        val normalized = goal.lowercase()
        val values = requestedSettingValues(normalized, "volume").toMutableSet()
        if (Regex("\\b(?:mute|muted|silence|silent)\\b").containsMatchIn(normalized)) values += 0
        if (Regex("\\b(?:full|maximum|max)\\s+(?:media\\s+)?volume\\b|\\b(?:media\\s+)?volume\\s+(?:full|maximum|max)\\b").containsMatchIn(normalized)) values += 100
        return values
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
        val intentBinding = ReasoningIntentBindingPolicy.evaluate(proposal.goal, proposal.actions)
        if (!intentBinding.allowed) {
            return Decision(false, "Proposed plan no longer matches explicit goal intent: ${intentBinding.reason}")
        }
        val grounding = ReasoningGroundingPolicy.evaluate(proposal.actions, normalized)
        if (!grounding.allowed) {
            return Decision(false, "Proposed plan no longer passes UI grounding: ${grounding.reason}")
        }
        val currentPlanFingerprint = PlanFingerprint.of(CommandPlanner.Plan(proposal.actions, emptyList()))
        if (currentPlanFingerprint != proposal.planFingerprint) {
            return Decision(false, "Proposed actions changed after admission; discard and replan")
        }

        return Decision(true, "Plan is intact, grounded, goal-bound, short-lived, and bound to the current UI observation")
    }
}
