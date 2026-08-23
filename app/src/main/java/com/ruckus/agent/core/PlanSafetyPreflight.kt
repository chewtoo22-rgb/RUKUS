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
    /** Stable semantic capability identifier for one exact confirmation-gated action. */
    fun approvalFingerprint(action: AgentAction): String = PlanFingerprint.of(listOf(action))

    fun evaluate(
        actions: List<AgentAction>,
        startStep: Int = 0,
        approved: Boolean = false,
        approvedActionFingerprint: String? = null
    ): PlanSafetyPreflightDecision {
        if (startStep !in 0..actions.size) {
            return PlanSafetyPreflightDecision(false, reason = "Invalid plan start step $startStep for ${actions.size} actions")
        }

        // Never let one approval authorize multiple independent high-impact effects.
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
                Risk.CONFIRM -> {
                    val expectedApproval = approvalFingerprint(action)
                    if (!approved || approvedActionFingerprint != expectedApproval) {
                        val detail = if (approved && approvedActionFingerprint != null) {
                            " Approval did not match the exact pending action."
                        } else {
                            ""
                        }
                        return PlanSafetyPreflightDecision(
                            allowed = false,
                            actionIndex = index,
                            action = action,
                            needsConfirmation = true,
                            reason = "Plan requires action-bound approval before any remaining actions run: ${decision.reason}$detail"
                        )
                    }
                }
                Risk.SAFE -> Unit
            }
        }

        return PlanSafetyPreflightDecision(true, reason = "All remaining actions passed safety preflight")
    }
}
