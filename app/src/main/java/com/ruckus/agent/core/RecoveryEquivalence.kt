package com.ruckus.agent.core

/**
 * Guards adaptive recovery so a different action may only satisfy the original
 * plan step when it preserves that step's intent. Safe does not automatically
 * mean equivalent: e.g. Home is safe, but it must never count as a successful Back.
 */
object RecoveryEquivalence {
    fun canSubstitute(original: AgentAction, alternate: AgentAction, confidence: Float): Boolean = when {
        original == alternate -> true
        original is AgentAction.TapLabel && alternate is AgentAction.TapLabel -> confidence >= .72f
        original is AgentAction.Scroll && alternate is AgentAction.Swipe -> swipeDirection(alternate) == original.direction
        original is AgentAction.Swipe && alternate is AgentAction.Scroll -> swipeDirection(original) == alternate.direction
        else -> false
    }

    private fun swipeDirection(action: AgentAction.Swipe): AgentAction.Direction =
        if (action.y2 < action.y1) AgentAction.Direction.DOWN else AgentAction.Direction.UP
}
