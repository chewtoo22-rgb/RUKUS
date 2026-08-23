package com.ruckus.agent.core

enum class Risk { SAFE, CONFIRM, BLOCKED }
data class SafetyDecision(val risk: Risk, val reason: String)

object SafetyGate {
    /**
     * Semantic controls that can create an irreversible or externally visible side effect must
     * never be treated as routine merely because Accessibility reports them as clickable.
     *
     * Keep this intentionally narrow and deterministic: these are action labels whose activation
     * commonly commits deletion, money movement, publication, messaging, installation, or a
     * device reset. The whole-plan preflight will surface the confirmation before any remaining
     * actions execute, and adaptive recovery will refuse to substitute one of these controls
     * because recovery only accepts SAFE alternates.
     */
    private val confirmationTapLabels = setOf(
        "delete",
        "delete account",
        "delete permanently",
        "erase",
        "erase all data",
        "factory reset",
        "reset phone",
        "uninstall",
        "install",
        "buy",
        "buy now",
        "purchase",
        "pay",
        "pay now",
        "place order",
        "submit order",
        "transfer",
        "send",
        "send message",
        "post",
        "publish",
    )

    internal fun normalizedSemanticLabel(label: String): String =
        label.lowercase().replace(Regex("\\s+"), " ").trim()

    fun classify(action: AgentAction): SafetyDecision = when (action) {
        is AgentAction.TapLabel -> {
            val normalized = normalizedSemanticLabel(action.label)
            if (normalized in confirmationTapLabels) {
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
