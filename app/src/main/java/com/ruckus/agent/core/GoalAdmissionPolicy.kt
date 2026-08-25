package com.ruckus.agent.core

/**
 * Bounds untrusted natural-language goals before parsing, planning, persistence, or reasoning.
 *
 * The executor intentionally supports short, deterministic phone goals rather than arbitrarily
 * large prompts. Rejecting oversized/control-character input at the first boundary prevents a
 * malformed request from becoming an unbounded parser/persistence surface while keeping the
 * original goal intact for all admitted work.
 */
object GoalAdmissionPolicy {
    const val MAX_GOAL_CHARS = 1024

    data class Decision(val allowed: Boolean, val normalizedGoal: String = "", val reason: String = "")

    fun evaluate(raw: String): Decision {
        val normalized = raw.trim()
        if (normalized.isEmpty()) return Decision(false, reason = "Goal is empty")
        if (normalized.length > MAX_GOAL_CHARS) {
            return Decision(false, reason = "Goal exceeds $MAX_GOAL_CHARS character limit")
        }
        if (normalized.any { it == '\u0000' || (it.isISOControl() && !it.isWhitespace()) }) {
            return Decision(false, reason = "Goal contains unsupported control characters")
        }
        return Decision(true, normalizedGoal = normalized)
    }
}
