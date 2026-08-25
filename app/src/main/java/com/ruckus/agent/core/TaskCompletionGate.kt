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
                val observedPkg = foregroundPackage(finalScreen)
                if(observedPkg.equals(last.packageName, ignoreCase = true))
                    CompletionDecision(true,"Final foreground package matches requested app")
                else CompletionDecision(false,"Requested app is not confirmed in the final foreground state")
            }
            is AgentAction.OpenAppByName -> {
                val observedPkg = foregroundPackage(finalScreen)
                val normalizedObservation = ObservedPlanProposal.normalizeObservation(finalScreen)
                val targetLabel = ReasoningGroundingPolicy.normalizeLabel(last.appName)
                val packages = normalizedObservation
                    ?.let(ReasoningGroundingPolicy::launchableLabelPackages)
                    ?.get(targetLabel)
                    .orEmpty()
                if(targetLabel.isEmpty()) {
                    CompletionDecision(false,"Requested app label is blank")
                } else if(packages.size != 1) {
                    CompletionDecision(false,"Requested app label is not uniquely bound in the final launchable-app inventory")
                } else if(observedPkg.equals(packages.single(), ignoreCase = true)) {
                    CompletionDecision(true,"Final foreground package matches the uniquely resolved requested app label")
                } else {
                    CompletionDecision(false,"Final foreground package does not match the requested app label")
                }
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

    private fun foregroundPackage(observation:String?):String? {
        if(observation == null) return null
        val marker = "pkg="
        val start = observation.indexOf(marker)
        if(start < 0) return null
        val valueStart = start + marker.length
        return observation.substring(valueStart)
            .takeWhile { !it.isWhitespace() && it != '|' }
            .trim()
            .takeIf { it.isNotEmpty() }
    }
}
