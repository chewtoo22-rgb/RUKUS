package com.ruckus.agent.core

data class CompletionRepairDecision(
    val action: AgentAction?,
    val reason: String
)

/**
 * Chooses a narrowly-scoped terminal repair when every planned step ran but the
 * final task goal can no longer be proven. Repairs must preserve the exact
 * terminal intent and are still subject to SafetyGate and RecoveryBudget.
 */
object TaskCompletionRepairPlanner {
    fun plan(terminalAction: AgentAction, completionFailure: String): CompletionRepairDecision = when (terminalAction) {
        is AgentAction.OpenApp -> CompletionRepairDecision(
            terminalAction,
            "Re-open the exact requested package to restore the terminal foreground goal"
        )
        is AgentAction.OpenAppByName -> CompletionRepairDecision(
            terminalAction,
            "Re-open the requested app to restore the terminal foreground goal"
        )
        is AgentAction.TypeText -> CompletionRepairDecision(
            terminalAction,
            "Re-apply the exact requested text once because final text evidence disappeared"
        )
        else -> CompletionRepairDecision(
            null,
            "No safe exact-intent completion repair for ${terminalAction::class.simpleName}: $completionFailure"
        )
    }
}
