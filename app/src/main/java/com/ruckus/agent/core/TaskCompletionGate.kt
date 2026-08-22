package com.ruckus.agent.core

data class CompletionDecision(val ok:Boolean,val reason:String)

/**
 * Final guard before a task is marked COMPLETE.
 * Individual actions may have been verified, but the task still needs a coherent
 * final checkpoint that is compatible with the user's terminal intent.
 */
object TaskCompletionGate {
    fun evaluate(plan: CommandPlanner.Plan, completedSteps: Int, finalScreen: String?): CompletionDecision {
        if(plan.actions.isEmpty()) return CompletionDecision(false,"Plan has no executable actions")
        if(completedSteps != plan.actions.size) return CompletionDecision(false,"Only $completedSteps/${plan.actions.size} steps are verified")

        val last=plan.actions.last()
        return when(last) {
            is AgentAction.OpenApp -> {
                if(finalScreen?.contains("pkg=${last.packageName}",ignoreCase=true)==true)
                    CompletionDecision(true,"Final foreground package matches requested app")
                else CompletionDecision(false,"Requested app is not confirmed in the final foreground state")
            }
            is AgentAction.OpenAppByName -> {
                if(finalScreen?.contains("pkg=",ignoreCase=true)==true)
                    CompletionDecision(true,"Final foreground app state is observable")
                else CompletionDecision(false,"Final foreground app could not be observed")
            }
            is AgentAction.TypeText -> {
                if(finalScreen?.contains(last.text,ignoreCase=true)==true)
                    CompletionDecision(true,"Final screen still contains the requested text")
                else CompletionDecision(false,"Requested text is not present in the final checkpoint")
            }
            AgentAction.Home, AgentAction.Back,
            is AgentAction.Tap, is AgentAction.TapLabel,
            is AgentAction.Swipe, is AgentAction.Scroll,
            AgentAction.InspectScreen -> {
                if(!finalScreen.isNullOrBlank()) CompletionDecision(true,"Final UI checkpoint is observable")
                else CompletionDecision(false,"Final UI checkpoint is unavailable")
            }
            is AgentAction.SetBrightness, is AgentAction.SetMediaVolume,
            is AgentAction.RunApprovedShell -> CompletionDecision(true,"Terminal action was already verified by its action-specific verifier")
        }
    }
}
