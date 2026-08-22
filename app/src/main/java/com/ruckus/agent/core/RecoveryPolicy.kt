package com.ruckus.agent.core

data class RecoveryDecision(
    val retry: Boolean,
    val inspectFirst: Boolean,
    val maxAttempts: Int = 1,
    val reason: String
)

object RecoveryPolicy {
    fun decide(action: AgentAction, failure: String): RecoveryDecision = when (action) {
        is AgentAction.TapLabel -> RecoveryDecision(true, true, 1, "Refresh visible UI then retry label lookup")
        is AgentAction.TypeText -> RecoveryDecision(true, true, 1, "Refresh focus state then retry text input")
        is AgentAction.Scroll -> RecoveryDecision(true, true, 1, "Refresh window state then retry scroll")
        AgentAction.Back, AgentAction.Home -> RecoveryDecision(true, false, 1, "Retry transient accessibility action")
        is AgentAction.OpenAppByName -> RecoveryDecision(false, false, reason = "Installed-app lookup failed; retry would repeat the same result")
        is AgentAction.OpenApp -> RecoveryDecision(false, false, reason = "Exact package launch failed")
        is AgentAction.SetBrightness -> RecoveryDecision(false, false, reason = "Settings permission/state must change before retry")
        is AgentAction.SetMediaVolume -> RecoveryDecision(false, false, reason = "Audio service rejected the action")
        is AgentAction.Tap, is AgentAction.Swipe -> RecoveryDecision(true, true, 1, "Refresh UI state then retry gesture")
        AgentAction.InspectScreen -> RecoveryDecision(false, false, reason = "Screen inspection itself failed")
        is AgentAction.RunApprovedShell -> RecoveryDecision(false, false, reason = "Privileged actions are never auto-retried")
    }
}
