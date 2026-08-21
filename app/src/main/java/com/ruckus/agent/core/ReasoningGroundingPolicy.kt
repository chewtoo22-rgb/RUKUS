package com.ruckus.agent.core

/**
 * Requires autonomous semantic actions to be grounded in the observation that produced the plan.
 * This prevents a reasoning planner from hallucinating a TapLabel target that is not actually
 * present on the inspected screen. Non-targeted navigation/actions remain governed by the
 * existing reasoning, admission, and safety policies.
 */
object ReasoningGroundingPolicy {
    data class Decision(val allowed: Boolean, val reason: String)

    fun evaluate(actions: List<AgentAction>, observation: String?): Decision {
        val normalized = ObservedPlanProposal.normalizeObservation(observation)
            ?: return Decision(false, "Planner observation is missing or not package-aware")
        val visible = visibleLabels(normalized)

        actions.forEachIndexed { index, action ->
            if (action is AgentAction.TapLabel) {
                val target = normalizeLabel(action.label)
                if (target.isEmpty()) {
                    return Decision(false, "Reasoning action ${index + 1} has a blank semantic target")
                }
                if (target !in visible) {
                    return Decision(
                        false,
                        "Reasoning action ${index + 1} targets '${action.label}', which is not visible in the inspected UI",
                    )
                }
            }
        }
        return Decision(true, "Semantic targets are grounded in the inspected UI")
    }

    internal fun visibleLabels(observation: String): Set<String> {
        val body = observation.substringAfter('|', observation.substringAfter('\n', ""))
        if (body.isBlank() || body.contains("No readable labels", ignoreCase = true)) return emptySet()
        return body
            .split('•', '\n')
            .map { it.substringAfter("text=", it).trim() }
            .map(::normalizeLabel)
            .filter { it.isNotEmpty() && !it.startsWith("pkg=") }
            .toSet()
    }

    internal fun normalizeLabel(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
}
