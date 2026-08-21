package com.ruckus.agent.core

/**
 * Additional admission boundary for observation-bound plans produced by a reasoning layer.
 *
 * Deterministic/manual execution paths keep their existing capabilities. A reasoning planner,
 * however, must stay inside a narrower action vocabulary until the executor can ground and verify
 * more powerful primitives safely. In particular, raw coordinate taps are brittle and approved
 * shell commands are privileged; neither may be introduced autonomously by planner output.
 */
object ReasoningPlanPolicy {
    data class Decision(val allowed: Boolean, val reason: String)

    fun evaluate(actions: List<AgentAction>): Decision {
        actions.forEachIndexed { index, action ->
            val problem = when (action) {
                is AgentAction.Tap -> "raw coordinate taps are not admitted from reasoning output"
                is AgentAction.RunApprovedShell -> "privileged shell actions are not admitted from reasoning output"
                else -> null
            }
            if (problem != null) return Decision(false, "Step ${index + 1}: $problem")
        }

        return Decision(true, "Reasoning plan stays inside the autonomous action vocabulary")
    }
}
