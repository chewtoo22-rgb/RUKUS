package com.ruckus.agent.core

data class VerificationResult(val ok:Boolean,val reason:String)

object ActionVerifier {
    fun verify(action:AgentAction,before:String?,after:String?,result:String?):VerificationResult {
        return when(action) {
            AgentAction.Home, AgentAction.Back,
            is AgentAction.OpenApp, is AgentAction.OpenAppByName,
            is AgentAction.Tap, is AgentAction.TapLabel,
            is AgentAction.Swipe, is AgentAction.Scroll -> {
                if(before != null && after != null && before != after) VerificationResult(true,"Visible UI changed")
                else if(result != null) VerificationResult(true,"Action adapter reported success")
                else VerificationResult(false,"No visible UI change or success result")
            }
            is AgentAction.TypeText -> {
                if(after?.contains(action.text, ignoreCase = true) == true) VerificationResult(true,"Typed text is visible")
                else if(result != null) VerificationResult(true,"Text adapter reported success")
                else VerificationResult(false,"Typed text not observed")
            }
            is AgentAction.SetBrightness, is AgentAction.SetMediaVolume ->
                if(result != null) VerificationResult(true,result) else VerificationResult(false,"Setting change was not acknowledged")
            AgentAction.InspectScreen -> VerificationResult(after != null,"Screen inspection completed")
            is AgentAction.RunApprovedShell -> VerificationResult(result != null,result ?: "Privileged action not acknowledged")
        }
    }
}
