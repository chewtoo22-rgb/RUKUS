package com.ruckus.agent.core

/**
 * Additional admission boundary for observation-bound plans produced by a reasoning layer.
 *
 * Deterministic/manual execution paths keep their existing capabilities. A reasoning planner,
 * however, must stay inside a narrower action vocabulary until the executor can ground and verify
 * more powerful primitives safely. Raw coordinate taps and approved shell commands are excluded.
 * App launches are admitted only through ReasoningGroundingPolicy, which requires the exact target
 * to exist in the trusted launchable-app inventory embedded by DeviceController in the inspected
 * observation. Model-supplied package names or labels therefore cannot authorize themselves.
 *
 * Reasoning proposals are also limited to a single state-changing action per inspected UI state.
 * A second mutation must be derived from a fresh observation and a newly admitted proposal. This
 * closes the stale-mid-plan gap where the first action changes the screen and later actions still
 * execute from assumptions made against the old UI.
 */
object ReasoningPlanPolicy {
    data class Decision(val allowed: Boolean, val reason: String)

    const val MAX_STATE_CHANGING_ACTIONS = 1

    fun evaluate(actions: List<AgentAction>): Decision {
        var stateChangingActions = 0

        actions.forEachIndexed { index, action ->
            val problem = when (action) {
                is AgentAction.Tap -> "raw coordinate taps are not admitted from reasoning output"
                is AgentAction.RunApprovedShell -> "privileged shell actions are not admitted from reasoning output"
                else -> null
            }
            if (problem != null) return Decision(false, "Step ${index + 1}: $problem")

            if (isStateChanging(action)) {
                stateChangingActions += 1
                if (stateChangingActions > MAX_STATE_CHANGING_ACTIONS) {
                    return Decision(
                        false,
                        "Step ${index + 1}: reasoning plans may perform only $MAX_STATE_CHANGING_ACTIONS state-changing action per observation; re-inspect and replan before the next mutation",
                    )
                }
            }
        }

        return Decision(true, "Reasoning plan stays inside the grounded autonomous vocabulary and one-mutation observation horizon")
    }

    internal fun isStateChanging(action: AgentAction): Boolean = when (action) {
        AgentAction.InspectScreen -> false
        else -> true
    }
}
