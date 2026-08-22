package com.ruckus.agent.core

/**
 * Requires autonomous semantic actions to be grounded in the observation that produced the plan.
 * Tap targets must be visible, explicitly clickable, explicitly enabled, and unambiguous in the
 * structured UI snapshot. Text entry is stricter: a reasoning proposal may type only when the
 * inspected UI proves that exactly one enabled, editable, non-sensitive input owns input focus.
 * Scroll actions are also target-sensitive: exactly one explicitly enabled, scrollable accessibility
 * node must be present so the planner cannot guess which container should receive the gesture.
 * App launches require an exact match in the trusted launchable-app inventory captured by the
 * controller during inspection. App-name launches require one unique exact normalized label match.
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
        val enabledScrollableCount = enabledScrollableNodeCount(normalized)
        val launchablePackages = launchablePackageCounts(normalized)
        val launchableLabels = launchableLabelPackages(normalized)

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
                is AgentAction.Scroll -> {
                    if (enabledScrollableCount == 0) {
                        return Decision(false, "Reasoning action ${index + 1} cannot scroll because the inspected UI does not prove an enabled scrollable container")
                    }
                    if (enabledScrollableCount > 1) {
                        return Decision(false, "Reasoning action ${index + 1} cannot scroll because $enabledScrollableCount enabled scrollable containers make the target ambiguous")
                    }
                }
                is AgentAction.OpenApp -> {
                    val target = normalizePackage(action.packageName)
                    val matches = launchablePackages[target] ?: 0
                    if (target.isEmpty() || matches == 0) {
                        return Decision(false, "Reasoning action ${index + 1} cannot launch '${action.packageName}' because the trusted launchable-app inventory does not contain that package")
                    }
                    if (matches != 1) {
                        return Decision(false, "Reasoning action ${index + 1} cannot launch '${action.packageName}' because the trusted launchable-app inventory is ambiguous")
                    }
                }
                is AgentAction.OpenAppByName -> {
                    val target = normalizeLabel(action.appName)
                    val packages = launchableLabels[target].orEmpty()
                    if (target.isEmpty() || packages.isEmpty()) {
                        return Decision(false, "Reasoning action ${index + 1} cannot launch '${action.appName}' because the trusted launchable-app inventory does not contain that exact app label")
                    }
                    if (packages.size != 1) {
                        return Decision(false, "Reasoning action ${index + 1} cannot launch '${action.appName}' because ${packages.size} launchable apps share that label")
                    }
                }
                else -> Unit
            }
        }
        return Decision(true, "Reasoning actions are grounded in current accessibility state or the trusted launchable-app inventory")
    }

    internal fun visibleLabels(observation: String): Set<String> = nodeTokens(observation)
        .mapNotNull(::nodeLabel)
        .map(::normalizeLabel)
        .filter(String::isNotEmpty)
        .toSet()

    internal fun clickableLabels(observation: String): Set<String> = clickableLabelCounts(observation).keys

    internal fun clickableLabelCounts(observation: String): Map<String, Int> = nodeTokens(observation)
        .filter { token -> hasFlag(token, "clickable", true) }
        .mapNotNull(::nodeLabel)
        .map(::normalizeLabel)
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()

    internal fun enabledClickableLabelCounts(observation: String): Map<String, Int> = nodeTokens(observation)
        .filter { token -> hasFlag(token, "clickable", true) && hasFlag(token, "enabled", true) }
        .mapNotNull(::nodeLabel)
        .map(::normalizeLabel)
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()

    internal fun focusedEditableNodeCount(observation: String): Int = nodeTokens(observation).count { token ->
        hasFlag(token, "editable", true) && hasFlag(token, "focused", true)
    }

    internal fun focusedEnabledEditableNodeCount(observation: String): Int = nodeTokens(observation).count { token ->
        hasFlag(token, "enabled", true) && hasFlag(token, "editable", true) && hasFlag(token, "focused", true)
    }

    internal fun focusedSensitiveNodeCount(observation: String): Int = nodeTokens(observation).count { token ->
        hasFlag(token, "editable", true) && hasFlag(token, "sensitive", true) && hasFlag(token, "focused", true)
    }

    internal fun focusedNonSensitiveNodeCount(observation: String): Int = nodeTokens(observation).count { token ->
        hasFlag(token, "editable", true) && hasFlag(token, "sensitive", false) && hasFlag(token, "focused", true)
    }

    internal fun enabledScrollableNodeCount(observation: String): Int = nodeTokens(observation).count { token ->
        hasFlag(token, "enabled", true) && hasFlag(token, "scrollable", true)
    }

    internal fun launchablePackageCounts(observation: String): Map<String, Int> = appTokens(observation)
        .mapNotNull(::appPackage)
        .map(::normalizePackage)
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()

    internal fun launchableLabelPackages(observation: String): Map<String, Set<String>> {
        val pairs = appTokens(observation).mapNotNull { token ->
            val label = appLabel(token)?.let(::normalizeLabel)?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val pkg = appPackage(token)?.let(::normalizePackage)?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            label to pkg
        }.toList()
        return pairs.groupBy({ it.first }, { it.second }).mapValues { (_, packages) -> packages.toSet() }
    }

    private fun bodyTokens(observation: String): Sequence<String> {
        val body = observation.substringAfter('|', observation.substringAfter('\n', ""))
        return body.split('•', '\n').asSequence().map(String::trim)
    }

    private fun nodeTokens(observation: String): Sequence<String> = bodyTokens(observation)
        .filter { it.startsWith("node[") }

    private fun appTokens(observation: String): Sequence<String> = bodyTokens(observation)
        .filter { it.startsWith("app[") }

    private fun nodeLabel(token: String): String? {
        val encoded = token.removePrefix("node[").substringBefore(";clickable=").substringAfter("text=", "")
        return decodeValue(encoded)
    }

    private fun appPackage(token: String): String? {
        val encoded = token.removePrefix("app[").substringBefore(";label=").substringAfter("package=", "")
        return decodeValue(encoded)
    }

    private fun appLabel(token: String): String? {
        val encoded = token.substringAfter(";label=", "").removeSuffix("]")
        return decodeValue(encoded)
    }

    private fun decodeValue(encoded: String): String? {
        if (encoded.isBlank()) return null
        return encoded.replace("\\;", ";").replace("\\]", "]").replace("\\\\", "\\")
    }

    private fun hasFlag(token: String, name: String, value: Boolean): Boolean {
        val marker = ";$name=$value"
        return token.contains("$marker;") || token.endsWith("$marker]")
    }

    internal fun normalizeLabel(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")
    internal fun normalizePackage(value: String): String = value.trim().lowercase()
}
