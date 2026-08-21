package com.ruckus.agent.core

import kotlin.math.max

data class AdaptiveRecoveryPlan(
    val alternate: AgentAction?,
    val reason: String,
    val confidence: Float = 0f
)

/**
 * Bounded, deterministic replanning from the latest visible screen state.
 * Never invents privileged actions and returns at most one safe alternate.
 */
object AdaptiveRecoveryPlanner {
    fun replan(action: AgentAction, screen: String?, failure: String): AdaptiveRecoveryPlan = when (action) {
        is AgentAction.TapLabel -> replanTap(action, screen)
        is AgentAction.Scroll -> AdaptiveRecoveryPlan(
            alternate = if (action.direction == AgentAction.Direction.DOWN)
                AgentAction.Swipe(500f, 1500f, 500f, 500f, 400)
            else AgentAction.Swipe(500f, 500f, 500f, 1500f, 400),
            reason = "Semantic scroll failed; use one bounded gesture fallback",
            confidence = .80f
        )
        is AgentAction.Swipe -> {
            val direction = if (action.y2 < action.y1) AgentAction.Direction.DOWN else AgentAction.Direction.UP
            AdaptiveRecoveryPlan(AgentAction.Scroll(direction), "Raw gesture failed; try semantic scroll", .76f)
        }
        AgentAction.Back -> AdaptiveRecoveryPlan(AgentAction.Home, "Back navigation failed; return to a known safe home state", .70f)
        else -> AdaptiveRecoveryPlan(null, "No safe alternate action for ${action::class.simpleName}: $failure")
    }

    private fun replanTap(action: AgentAction.TapLabel, screen: String?): AdaptiveRecoveryPlan {
        val target = normalize(action.label)
        val labels = parseLabels(screen)
        val ranked = labels.map { it to similarity(target, normalize(it)) }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return AdaptiveRecoveryPlan(null, "No visible labels available for alternate tap")
        val second = ranked.getOrNull(1)?.second ?: 0f
        val uniqueEnough = best.second >= .72f && best.second - second >= .08f
        return if (uniqueEnough) AdaptiveRecoveryPlan(
            AgentAction.TapLabel(best.first),
            "Target '${action.label}' not found; closest unique visible label is '${best.first}'",
            best.second
        ) else AdaptiveRecoveryPlan(null, "No uniquely close visible label for '${action.label}'")
    }

    private fun parseLabels(screen: String?): List<String> = screen.orEmpty()
        .substringAfter("labels=", screen.orEmpty())
        .substringBefore(" | ")
        .split(" • ")
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("pkg=") }
        .distinct()

    private fun normalize(value: String) = value.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()

    private fun similarity(a: String, b: String): Float {
        if (a.isBlank() || b.isBlank()) return 0f
        if (a == b) return 1f
        if (a in b || b in a) return .90f
        val distance = levenshtein(a, b)
        return 1f - distance.toFloat() / max(a.length, b.length).coerceAtLeast(1)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val cur = IntArray(b.length + 1)
            cur[0] = i + 1
            for (j in b.indices) cur[j + 1] = minOf(
                cur[j] + 1,
                prev[j + 1] + 1,
                prev[j] + if (a[i] == b[j]) 0 else 1
            )
            prev = cur
        }
        return prev[b.length]
    }
}
