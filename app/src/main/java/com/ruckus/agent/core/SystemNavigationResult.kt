package com.ruckus.agent.core

object SystemNavigationResult {
    fun requirePerformed(actionName: String, performed: Boolean): String {
        require(actionName.isNotBlank()) { "actionName must not be blank" }
        check(performed) { "$actionName global action was rejected by Accessibility" }
        return actionName
    }
}
