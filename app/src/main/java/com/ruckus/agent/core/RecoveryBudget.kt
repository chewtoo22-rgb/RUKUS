package com.ruckus.agent.core

data class RecoveryBudgetDecision(val allowed:Boolean,val reason:String)

/**
 * Caps autonomous recovery work across an entire task, including resumed tasks.
 * This prevents a long plan from thrashing through many individually-safe retries.
 */
object RecoveryBudget {
    const val MAX_TOTAL_ATTEMPTS = 3

    fun decide(attemptsUsed:Int):RecoveryBudgetDecision = when {
        attemptsUsed < 0 -> RecoveryBudgetDecision(false,"Invalid recovery attempt count")
        attemptsUsed >= MAX_TOTAL_ATTEMPTS -> RecoveryBudgetDecision(false,"Recovery budget exhausted ($attemptsUsed/$MAX_TOTAL_ATTEMPTS)")
        else -> RecoveryBudgetDecision(true,"Recovery attempt ${attemptsUsed+1}/$MAX_TOTAL_ATTEMPTS allowed")
    }
}
