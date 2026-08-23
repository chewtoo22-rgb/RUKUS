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
                if(launchedPkg.isNotEmpty() && after?.contains("pkg=$launchedPkg",ignoreCase=true)==true) {
                    VerificationResult(true,"Resolved app package is foreground")
                } else if(launchedPkg.isEmpty()) {
                    VerificationResult(false,"App launch result did not identify the resolved package")
                } else {
                    VerificationResult(false,"Resolved app package was not observed in the foreground")
                }
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
            is AgentAction.SetBrightness -> verifyBrightness(action, after)
            is AgentAction.SetMediaVolume -> verifyMediaVolume(action, after)
            AgentAction.InspectScreen -> VerificationResult(after?.startsWith("pkg=")==true,"Package-aware screen inspection completed")
            is AgentAction.RunApprovedShell -> VerificationResult(result != null,result ?: "Privileged action not acknowledged")
        }
    }

    private fun verifyBrightness(action:AgentAction.SetBrightness, after:String?):VerificationResult {
        val observed = stateInt(after, "brightness")
            ?: return VerificationResult(false,"Brightness state was not observable after the change")
        val expected = (action.percent * 255 / 100).coerceIn(1,255)
        return if(observed == expected) VerificationResult(true,"Observed brightness matches requested setting")
        else VerificationResult(false,"Brightness verification mismatch: expected raw=$expected observed=$observed")
    }

    private fun verifyMediaVolume(action:AgentAction.SetMediaVolume, after:String?):VerificationResult {
        val observed = stateInt(after, "media")
            ?: return VerificationResult(false,"Media volume state was not observable after the change")
        val max = stateInt(after, "mediaMax")
            ?: return VerificationResult(false,"Media volume maximum was not observable after the change")
        if(max < 0) return VerificationResult(false,"Media volume maximum was invalid after the change")
        val expected = (max * action.percent / 100).coerceIn(0,max)
        return if(observed == expected) VerificationResult(true,"Observed media volume matches requested setting")
        else VerificationResult(false,"Media volume verification mismatch: expected=$expected/$max observed=$observed/$max")
    }

    private fun stateInt(observation:String?, key:String):Int? {
        if(observation == null) return null
        val state = observation.substringAfter("state[","").substringBefore("]","")
        if(state.isBlank()) return null
        return state.split(';')
            .mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if(separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
            }
            .firstOrNull { (name, _) -> name == key }
            ?.second
            ?.toIntOrNull()
    }
}
