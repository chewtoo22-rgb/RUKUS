package com.ruckus.agent.core

data class CrashRecoveryDecision(
    val replayAllowed: Boolean,
    val reason: String
)

/**
 * Decides whether an action with an ambiguous crash-time outcome may be replayed.
 * Only actions whose replay is bounded and effectively idempotent are allowed.
 */
object CrashRecoveryPolicy {
    fun decide(action: AgentAction): CrashRecoveryDecision = when (action) {
        is AgentAction.OpenApp,
        is AgentAction.OpenAppByName,
        is AgentAction.SetBrightness,
        is AgentAction.SetMediaVolume,
        AgentAction.Home,
        AgentAction.InspectScreen -> CrashRecoveryDecision(true, "Replay is bounded and idempotent enough for crash recovery")

        AgentAction.Back,
        is AgentAction.Tap,
        is AgentAction.TapLabel,
        is AgentAction.TypeText,
        is AgentAction.Swipe,
        is AgentAction.Scroll -> CrashRecoveryDecision(false, "Action outcome is ambiguous; replay could duplicate or compound a side effect")

        is AgentAction.RunApprovedShell -> CrashRecoveryDecision(false, "Privileged actions are never replayed after an ambiguous crash")
    }
}
