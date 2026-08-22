package com.ruckus.agent.core

/**
 * Requires autonomous semantic actions to be grounded in the observation that produced the plan.
 * Tap targets must be visible, explicitly clickable, explicitly enabled, and unambiguous in the
 * structured UI snapshot. Text entry is stricter: a reasoning proposal may type only when the
 * inspected UI proves that exactly one enabled, editable, non-sensitive input owns input focus.
 * This keeps a planner from inventing interaction affordances, targeting disabled controls,
 * guessing between duplicate controls, ambiguous typing contexts, or autonomously writing into secrets.
 */
object ReasoningGroundingPolicy {
    data class Decision(val allowed: Boolean, val reason: String)

    fun evaluate(actions: List<AgentAction>, observation: String?): Decision {
        val normalized = ObservedPlanProposal.normalizeObservation(observation)
            ?: return Decision(false, "Planner observation is missing or not package-aware")
        val visible = visibleLabels(normalized)
        val clickableCounts = clickableLabelCounts(normalized)
        val enabledClickableCounts = enabledClickableLabelCounts(normalized)
        val focusedEditableCount = focusedEditableNodeCount(normalized)
        val focusedEnabledEditableCount = focusedEnabledEditableNodeCount(normalized)
        val focusedSensitiveCount = focusedSensitiveNodeCount(normalized)
        val focusedNonSensitiveCount = focusedNonSensitiveNodeCount(normalized)

        actions.forEachIndexed { index, action ->
            when (action) {
                is AgentAction.TapLabel -> {
                    val target = normalizeLabel(action.label)
                    if (target.isEmpty()) {
                        return Decision(false, "Reasoning action ${index + 1} has a blank semantic target")
                    }
                    if (target !in visible) {
                        return Decision(false, "Reasoning action ${index + 1} targets '${action.label}', which is not visible in the inspected UI")
                    }
                    val clickableMatches = clickableCounts[target] ?: 0
                    if (clickableMatches == 0) {
                        return Decision(false, "Reasoning action ${index + 1} targets '${action.label}', but the inspected UI does not prove that target is clickable")
                    }
                    if (clickableMatches > 1) {
                        return Decision(false, "Reasoning action ${index + 1} targets '${action.label}', but $clickableMatches clickable matches make the target ambiguous")
                    }
                    val enabledMatches = enabledClickableCounts[target] ?: 0
                    if (enabledMatches != 1) {
                        return Decision(false, "Reasoning action ${index + 1} targets '${action.label}', but the inspected UI does not prove that the clickable target is enabled")
                    }
                }
                is AgentAction.TypeText -> {
                    if (focusedEditableCount == 0) {
                        return Decision(false, "Reasoning action ${index + 1} cannot type because the inspected UI does not prove a focused editable field")
                    }
                    if (focusedEditableCount > 1) {
                        return Decision(false, "Reasoning action ${index + 1} cannot type because $focusedEditableCount focused editable fields make the typing target ambiguous")
                    }
                    if (focusedEnabledEditableCount != 1) {
                        return Decision(false, "Reasoning action ${index + 1} cannot type because the focused editable field is not explicitly proven enabled")
                    }
                    if (focusedSensitiveCount > 0) {
                        return Decision(false, "Reasoning action ${index + 1} cannot autonomously type into a sensitive field")
                    }
                    if (focusedNonSensitiveCount != 1) {
                        return Decision(false, "Reasoning action ${index + 1} cannot type because field sensitivity is not explicitly proven safe")
                    }
                }
                else -> Unit
            }
        }
        return Decision(true, "Semantic targets are uniquely grounded with proven enabled click affordances, and text-entry context is uniquely focused, enabled, and explicitly non-sensitive")
    }

    internal fun visibleLabels(observation: String): Set<String> = nodeTokens(observation)
        .mapNotNull(::nodeLabel)
        .map(::normalizeLabel)
        .filter(String::isNotEmpty)
        .toSet()

    internal fun clickableLabels(observation: String): Set<String> = clickableLabelCounts(observation).keys

    internal fun clickableLabelCounts(observation: String): Map<String, Int> = nodeTokens(observation)
        .filter { token -> token.contains(";clickable=true;") }
        .mapNotNull(::nodeLabel)
        .map(::normalizeLabel)
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()

    internal fun enabledClickableLabelCounts(observation: String): Map<String, Int> = nodeTokens(observation)
        .filter { token -> token.contains(";clickable=true;") && token.contains(";enabled=true;") }
        .mapNotNull(::nodeLabel)
        .map(::normalizeLabel)
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()

    internal fun focusedEditableNodeCount(observation: String): Int = nodeTokens(observation).count { token ->
        token.contains(";editable=true;") && token.contains(";focused=true]")
    }

    internal fun focusedEnabledEditableNodeCount(observation: String): Int = nodeTokens(observation).count { token ->
        token.contains(";enabled=true;") && token.contains(";editable=true;") && token.contains(";focused=true]")
    }

    internal fun focusedSensitiveNodeCount(observation: String): Int = nodeTokens(observation).count { token ->
        token.contains(";editable=true;") && token.contains(";sensitive=true;") && token.contains(";focused=true]")
    }

    internal fun focusedNonSensitiveNodeCount(observation: String): Int = nodeTokens(observation).count { token ->
        token.contains(";editable=true;") && token.contains(";sensitive=false;") && token.contains(";focused=true]")
    }

    private fun nodeTokens(observation: String): Sequence<String> {
        val body = observation.substringAfter('|', observation.substringAfter('\n', ""))
        return body.split('•', '\n').asSequence().map(String::trim).filter { it.startsWith("node[") }
    }

    private fun nodeLabel(token: String): String? {
        val encoded = token.removePrefix("node[").substringBefore(";clickable=").substringAfter("text=", "")
        if (encoded.isBlank()) return null
        return encoded.replace("\\;", ";").replace("\\]", "]").replace("\\\\", "\\")
    }

    internal fun normalizeLabel(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")
}
