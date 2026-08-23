package com.ruckus.agent.core

data class PlanSafetyPreflightDecision(
    val allowed: Boolean,
    val actionIndex: Int? = null,
    val action: AgentAction? = null,
    val needsConfirmation: Boolean = false,
    val reason: String
)

/**
 * Evaluates every remaining action before execution begins.
 * This prevents a plan from performing earlier safe side effects and only then
 * discovering that a later step is blocked or requires approval.
 */
object PlanSafetyPreflight {
    fun evaluate(actions: List<AgentAction>, startStep: Int = 0, approved: Boolean = false): PlanSafetyPreflightDecision {
        if (startStep !in 0..actions.size) {
            return PlanSafetyPreflightDecision(false, reason = "Invalid plan start step $startStep for ${actions.size} actions")
        }

        // A single boolean approval must never become a blanket authorization for multiple
        // independent high-impact side effects. Until approvals are represented as durable,
        // action-bound capabilities, require confirmation-gated operations to be split into
        // separate tasks so each one receives its own explicit approval boundary.
        val confirmationIndices = (startStep until actions.size).filter { index ->
            SafetyGate.classify(actions[index]).risk == Risk.CONFIRM
        }
        if (confirmationIndices.size > 1) {
            val firstIndex = confirmationIndices.first()
            return PlanSafetyPreflightDecision(
                allowed = false,
                actionIndex = firstIndex,
                action = actions[firstIndex],
                reason = "Plan contains ${confirmationIndices.size} confirmation-required actions; split high-impact operations into separate tasks so each receives explicit approval"
            )
        }

        for (index in startStep until actions.size) {
            val action = actions[index]
            val decision = SafetyGate.classify(action)
            when (decision.risk) {
                Risk.BLOCKED -> return PlanSafetyPreflightDecision(
                    allowed = false,
                    actionIndex = index,
                    action = action,
                    reason = "Plan blocked before execution: ${decision.reason}"
                )
                Risk.CONFIRM -> if (!approved) return PlanSafetyPreflightDecision(
                    allowed = false,
                    actionIndex = index,
                    action = action,
                    needsConfirmation = true,
                    reason = "Plan requires approval before any remaining actions run: ${decision.reason}"
                )
                Risk.SAFE -> Unit
            }
        }

        return PlanSafetyPreflightDecision(true, reason = "All remaining actions passed safety preflight")
    }
}
