package com.ruckus.agent.core

/**
 * Hard admission boundary for any plan before it reaches execution.
 *
 * This is intentionally independent of SafetyGate. SafetyGate answers whether an individual
 * action is safe/confirmable; this policy answers whether the proposed plan is bounded and
 * structurally sane enough to execute at all. Keeping that distinction lets a future reasoning
 * planner propose typed AgentActions without gaining an unbounded execution path.
 */
object PlanAdmissionPolicy {
    const val MAX_ACTIONS = 8
    const val MAX_TEXT_LENGTH = 240
    const val MAX_SHELL_ARGS = 8

    data class Decision(val allowed: Boolean, val reason: String)

    fun evaluate(actions: List<AgentAction>): Decision {
        if (actions.isEmpty()) return Decision(false, "Plan contains no actions")
        if (actions.size > MAX_ACTIONS) {
            return Decision(false, "Plan has ${actions.size} actions; maximum bounded horizon is $MAX_ACTIONS")
        }

        actions.forEachIndexed { index, action ->
            val problem = validateAction(action)
            if (problem != null) return Decision(false, "Step ${index + 1}: $problem")
        }

        return Decision(true, "Plan admitted: ${actions.size} bounded action(s)")
    }

    private fun validateAction(action: AgentAction): String? = when (action) {
        is AgentAction.OpenApp -> when {
            action.packageName.isBlank() -> "package name is blank"
            action.packageName.length > MAX_TEXT_LENGTH -> "package name is too long"
            else -> null
        }
        is AgentAction.OpenAppByName -> validateTarget("app name", action.appName)
        is AgentAction.Tap -> validatePoint("tap", action.x, action.y)
        is AgentAction.TapLabel -> validateTarget("tap label", action.label)
        is AgentAction.TypeText -> when {
            action.text.isEmpty() -> "text payload is empty"
            action.text.length > MAX_TEXT_LENGTH -> "text payload exceeds $MAX_TEXT_LENGTH characters"
            else -> null
        }
        is AgentAction.SetBrightness -> if (action.percent !in 0..100) "brightness is outside 0..100" else null
        is AgentAction.SetMediaVolume -> if (action.percent !in 0..100) "media volume is outside 0..100" else null
        is AgentAction.Swipe -> when {
            action.durationMs !in 1..5000 -> "swipe duration is outside 1..5000 ms"
            validatePoint("swipe start", action.x1, action.y1) != null -> validatePoint("swipe start", action.x1, action.y1)
            validatePoint("swipe end", action.x2, action.y2) != null -> validatePoint("swipe end", action.x2, action.y2)
            action.x1 == action.x2 && action.y1 == action.y2 -> "swipe start and end points are identical"
            else -> null
        }
        is AgentAction.RunApprovedShell -> when {
            action.commandId.isBlank() -> "approved shell command id is blank"
            action.commandId.length > MAX_TEXT_LENGTH -> "approved shell command id is too long"
            action.args.size > MAX_SHELL_ARGS -> "approved shell command has too many arguments"
            action.args.any { (key, value) -> key.isBlank() || key.length > MAX_TEXT_LENGTH || value.length > MAX_TEXT_LENGTH } ->
                "approved shell command contains an invalid argument"
            else -> ApprovedShellCommandPolicy.evaluate(action.commandId, action.args)
                .takeIf { !it.allowed }
                ?.reason
        }
        else -> null
    }

    private fun validatePoint(name: String, x: Float, y: Float): String? = when {
        !x.isFinite() || !y.isFinite() -> "$name coordinates are not finite"
        x < 0f || y < 0f -> "$name coordinates are negative"
        else -> null
    }

    private fun validateTarget(name: String, value: String): String? = when {
        value.isBlank() -> "$name is blank"
        value.length > MAX_TEXT_LENGTH -> "$name exceeds $MAX_TEXT_LENGTH characters"
        else -> null
    }
}
