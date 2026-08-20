package com.ruckus.agent.core

enum class Risk { SAFE, CONFIRM, BLOCKED }
data class SafetyDecision(val risk: Risk, val reason: String)

object SafetyGate {
    fun classify(action: AgentAction): SafetyDecision = when (action) {
        is AgentAction.OpenApp, AgentAction.Back, AgentAction.Home,
        is AgentAction.Tap, is AgentAction.TapLabel, is AgentAction.Swipe,
        is AgentAction.TypeText, is AgentAction.SetBrightness,
        is AgentAction.SetMediaVolume -> SafetyDecision(Risk.SAFE, "Routine device action")
        is AgentAction.RunApprovedShell -> SafetyDecision(Risk.CONFIRM, "Privileged action requires approval")
    }
}
