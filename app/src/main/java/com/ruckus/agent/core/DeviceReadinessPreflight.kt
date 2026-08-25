package com.ruckus.agent.core

data class DeviceReadinessDecision(
    val allowed: Boolean,
    val reason: String,
    val action: AgentAction? = null,
    val actionIndex: Int? = null
)

/**
 * Whole-plan device capability preflight.
 *
 * MIYAGI's executor depends on Accessibility observations to settle and verify every
 * action. A plan must therefore fail before dispatch when the observation source is
 * unavailable instead of partially executing blind. Permission-gated or adapter-gated
 * device writes are also checked across the entire remaining plan so a later step
 * cannot strand a task after earlier side effects have already run.
 */
object DeviceReadinessPreflight {
    fun evaluate(
        actions: List<AgentAction>,
        startStep: Int,
        accessibilityReady: Boolean,
        writeSettingsReady: Boolean,
        approvedShellReady: Boolean = false
    ): DeviceReadinessDecision {
        if (startStep !in 0..actions.size) {
            return DeviceReadinessDecision(false, "Invalid plan start step")
        }
        if (startStep == actions.size) {
            return DeviceReadinessDecision(true, "No remaining device actions")
        }
        if (!accessibilityReady) {
            return DeviceReadinessDecision(
                false,
                "Accessibility control service is offline; observation-bound execution cannot start",
                actions[startStep],
                startStep
            )
        }
        val brightnessIndex = (startStep until actions.size)
            .firstOrNull { actions[it] is AgentAction.SetBrightness }
        if (brightnessIndex != null && !writeSettingsReady) {
            return DeviceReadinessDecision(
                false,
                "WRITE_SETTINGS permission is required before this plan can change brightness",
                actions[brightnessIndex],
                brightnessIndex
            )
        }
        val shellIndex = (startStep until actions.size)
            .firstOrNull { actions[it] is AgentAction.RunApprovedShell }
        if (shellIndex != null && !approvedShellReady) {
            return DeviceReadinessDecision(
                false,
                "Approved shell adapter is unavailable; this plan cannot dispatch shell work",
                actions[shellIndex],
                shellIndex
            )
        }
        return DeviceReadinessDecision(true, "Required device capabilities are ready")
    }
}
