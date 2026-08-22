package com.ruckus.agent.core

data class VerificationResult(val ok:Boolean,val reason:String)

object ActionVerifier {
    fun verify(action:AgentAction,before:String?,after:String?,result:String?):VerificationResult {
        return when(action) {
            is AgentAction.OpenApp -> {
                if(after?.contains("pkg=${action.packageName}",ignoreCase=true)==true) VerificationResult(true,"Target package is foreground")
                else VerificationResult(false,"Target package not observed after launch")
            }
            is AgentAction.OpenAppByName -> {
                val launchedPkg=result?.substringAfter("package=","")?.trim().orEmpty()
                if(launchedPkg.isNotEmpty() && after?.contains("pkg=$launchedPkg",ignoreCase=true)==true) VerificationResult(true,"Resolved app package is foreground")
                else if(before!=null && after!=null && before!=after && result!=null) VerificationResult(true,"UI changed after app launch")
                else VerificationResult(false,"App launch could not be verified")
            }
            AgentAction.Home, AgentAction.Back -> {
                if(before != null && after != null && before != after) VerificationResult(true,"Visible UI changed after system navigation")
                else VerificationResult(false,"System navigation was not proven by an observable UI change")
            }
            is AgentAction.TapLabel -> {
                if(before != null && after != null && before != after) VerificationResult(true,"Visible UI changed after semantic tap")
                else VerificationResult(false,"Semantic tap was dispatched but produced no observable UI change")
            }
            is AgentAction.Scroll -> {
                if(before != null && after != null && before != after) VerificationResult(true,"Visible UI changed after semantic scroll")
                else VerificationResult(false,"Semantic scroll was dispatched but produced no observable UI change")
            }
            is AgentAction.Tap, is AgentAction.Swipe -> {
                if(before != null && after != null && before != after) VerificationResult(true,"Visible UI changed")
                else if(result != null) VerificationResult(true,"Action adapter reported success")
                else VerificationResult(false,"No visible UI change or success result")
            }
            is AgentAction.TypeText -> {
                val wasVisible = before?.contains(action.text, ignoreCase = true) == true
                val isVisible = after?.contains(action.text, ignoreCase = true) == true
                if(action.text.isNotBlank() && !wasVisible && isVisible) {
                    VerificationResult(true,"Typed text became visible after text entry")
                } else if(wasVisible && isVisible) {
                    VerificationResult(false,"Typed text was already visible before the action, so text entry was not proven")
                } else {
                    VerificationResult(false,"Typed text did not become visible after text entry")
                }
            }
            is AgentAction.SetBrightness, is AgentAction.SetMediaVolume ->
                if(result != null) VerificationResult(true,result) else VerificationResult(false,"Setting change was not acknowledged")
            AgentAction.InspectScreen -> VerificationResult(after?.startsWith("pkg=")==true,"Package-aware screen inspection completed")
            is AgentAction.RunApprovedShell -> VerificationResult(result != null,result ?: "Privileged action not acknowledged")
        }
    }
}
