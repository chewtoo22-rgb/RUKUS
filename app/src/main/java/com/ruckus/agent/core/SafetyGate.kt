package com.ruckus.agent.core

enum class Risk { SAFE, CONFIRM, BLOCKED }
data class SafetyDecision(val risk: Risk, val reason: String)

object SafetyGate {
    /**
     * Semantic controls that can create an irreversible or externally visible side effect must
     * never be treated as routine merely because Accessibility reports them as clickable.
     *
     * Match high-impact *verbs* at word boundaries instead of only exact button labels. Real
     * Android controls are frequently parameterized ("Delete photo", "Send $20", "Install
     * update", "Post comment"), so an exact-label allowlist can silently downgrade the same side
     * effect to SAFE when the UI adds an object or amount. Word-boundary matching keeps the rule
     * deterministic while avoiding substring accidents such as matching "payment" as "pay".
     *
     * The whole-plan preflight surfaces confirmation before any remaining actions execute, and
     * adaptive recovery refuses to substitute these controls because recovery accepts SAFE
     * alternates only.
     */
    private val confirmationTapPatterns = listOf(
        Regex("\\bdelete\\b"),
        Regex("\\berase\\b"),
        Regex("\\bfactory\\s+reset\\b"),
        Regex("\\breset\\s+(phone|device|password|account)\\b"),
        Regex("\\buninstall\\b"),
        Regex("\\binstall\\b"),
        Regex("\\bbuy\\b"),
        Regex("\\bpurchase\\b"),
        Regex("\\bpay\\b"),
        Regex("\\bplace\\s+order\\b"),
        Regex("\\bsubmit\\s+order\\b"),
        Regex("\\btransfer\\b"),
        Regex("\\bsend\\b"),
        Regex("\\bpost\\b"),
        Regex("\\bpublish\\b"),
        Regex("\\bshare\\b"),
        Regex("\\bcall\\b"),
    )

    internal fun normalizedSemanticLabel(label: String): String =
        label.lowercase().replace(Regex("\\s+"), " ").trim()

    internal fun requiresSemanticConfirmation(label: String): Boolean {
        val normalized = normalizedSemanticLabel(label)
        return confirmationTapPatterns.any { it.containsMatchIn(normalized) }
    }

    fun classify(action: AgentAction): SafetyDecision = when (action) {
        is AgentAction.TapLabel -> {
            val normalized = normalizedSemanticLabel(action.label)
            if (requiresSemanticConfirmation(action.label)) {
                SafetyDecision(Risk.CONFIRM, "High-impact semantic action '$normalized' requires approval")
            } else {
                SafetyDecision(Risk.SAFE, "Routine semantic device action")
            }
        }
        is AgentAction.OpenApp, is AgentAction.OpenAppByName,
        AgentAction.Back, AgentAction.Home, AgentAction.InspectScreen,
        is AgentAction.Tap, is AgentAction.Swipe,
        is AgentAction.Scroll, is AgentAction.TypeText,
        is AgentAction.SetBrightness, is AgentAction.SetMediaVolume -> SafetyDecision(Risk.SAFE, "Routine device action")
        is AgentAction.RunApprovedShell -> SafetyDecision(Risk.CONFIRM, "Privileged action requires approval")
    }
}
