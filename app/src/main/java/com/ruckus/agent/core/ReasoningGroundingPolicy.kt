package com.ruckus.agent.core

/**
 * Requires autonomous semantic actions to be grounded in the observation that produced the plan.
 * Tap targets must be both visible and explicitly clickable in the structured UI snapshot. Text
 * entry is stricter: a reasoning proposal may type only when the inspected UI proves that an
 * editable, non-sensitive input currently owns input focus. This keeps a planner from inventing
 * interaction affordances, typing context, or autonomously writing into password/secret fields.
 */
object ReasoningGroundingPolicy {
    data class Decision(val allowed: Boolean, val reason: String)

    fun evaluate(actions: List<AgentAction>, observation: String?): Decision {
        val normalized = ObservedPlanProposal.normalizeObservation(observation)
            ?: return Decision(false, "Planner observation is missing or not package-aware")
        val visible = visibleLabels(normalized)
        val clickable = clickableLabels(normalized)
        val focusedEditable = hasFocusedEditableNode(normalized)
        val focusedSensitive = hasFocusedSensitiveNode(normalized)
        val focusedNonSensitive = hasFocusedNonSensitiveNode(normalized)

        actions.forEachIndexed { index, action ->
            when (action) {
                is AgentAction.TapLabel -> {
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
                    if (target !in clickable) {
                        return Decision(
                            false,
                            "Reasoning action ${index + 1} targets '${action.label}', but the inspected UI does not prove that target is clickable",
                        )
                    }
                }
                is AgentAction.TypeText -> {
                    if (!focusedEditable) {
                        return Decision(
                            false,
                            "Reasoning action ${index + 1} cannot type because the inspected UI does not prove a focused editable field",
                        )
                    }
                    if (focusedSensitive) {
                        return Decision(
                            false,
                            "Reasoning action ${index + 1} cannot autonomously type into a sensitive field",
                        )
                    }
                    if (!focusedNonSensitive) {
                        return Decision(
                            false,
                            "Reasoning action ${index + 1} cannot type because field sensitivity is not explicitly proven safe",
                        )
                    }
                }
                else -> Unit
            }
        }
        return Decision(true, "Semantic targets, click affordances, and non-sensitive text-entry context are grounded in the inspected UI")
    }

    internal fun visibleLabels(observation: String): Set<String> = nodeTokens(observation)
        .mapNotNull(::nodeLabel)
        .map(::normalizeLabel)
        .filter(String::isNotEmpty)
        .toSet()

    internal fun clickableLabels(observation: String): Set<String> = nodeTokens(observation)
        .filter { token -> token.contains(";clickable=true;") }
        .mapNotNull(::nodeLabel)
        .map(::normalizeLabel)
        .filter(String::isNotEmpty)
        .toSet()

    internal fun hasFocusedEditableNode(observation: String): Boolean = nodeTokens(observation).any { token ->
        token.contains(";editable=true;") && token.contains(";focused=true]")
    }

    internal fun hasFocusedSensitiveNode(observation: String): Boolean = nodeTokens(observation).any { token ->
        token.contains(";editable=true;") &&
            token.contains(";sensitive=true;") &&
            token.contains(";focused=true]")
    }

    internal fun hasFocusedNonSensitiveNode(observation: String): Boolean = nodeTokens(observation).any { token ->
        token.contains(";editable=true;") &&
            token.contains(";sensitive=false;") &&
            token.contains(";focused=true]")
    }

    private fun nodeTokens(observation: String): Sequence<String> {
        val body = observation.substringAfter('|', observation.substringAfter('\n', ""))
        return body
            .split('•', '\n')
            .asSequence()
            .map(String::trim)
            .filter { it.startsWith("node[") }
    }

    private fun nodeLabel(token: String): String? {
        val encoded = token
            .removePrefix("node[")
            .substringBefore(";clickable=")
            .substringAfter("text=", "")
        if (encoded.isBlank()) return null
        return encoded
            .replace("\\;", ";")
            .replace("\\]", "]")
            .replace("\\\\", "\\")
    }

    internal fun normalizeLabel(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
}
