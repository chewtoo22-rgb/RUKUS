package com.ruckus.agent.core

import java.security.MessageDigest

/**
 * Stable semantic fingerprint of an executable plan. Used to ensure a persisted
 * checkpoint is resumed only against the exact action sequence that created it.
 */
object PlanFingerprint {
    fun of(plan: CommandPlanner.Plan): String = of(plan.actions)

    fun of(actions: List<AgentAction>): String {
        val canonical = actions.joinToString("\n") { canonicalAction(it) }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun canonicalAction(action: AgentAction): String = when (action) {
        is AgentAction.OpenApp -> "open-package:${escape(action.packageName)}"
        is AgentAction.OpenAppByName -> "open-name:${escape(action.appName)}"
        AgentAction.Back -> "back"
        AgentAction.Home -> "home"
        is AgentAction.Tap -> "tap:${action.x}:${action.y}"
        is AgentAction.TapLabel -> "tap-label:${escape(action.label)}"
        is AgentAction.Swipe -> "swipe:${action.x1}:${action.y1}:${action.x2}:${action.y2}:${action.durationMs}"
        is AgentAction.Scroll -> "scroll:${action.direction.name}"
        is AgentAction.TypeText -> "type:${escape(action.text)}"
        AgentAction.InspectScreen -> "inspect"
        is AgentAction.SetBrightness -> "brightness:${action.percent}"
        is AgentAction.SetMediaVolume -> "media-volume:${action.percent}"
        is AgentAction.RunApprovedShell -> {
            val args = action.args.toSortedMap().entries.joinToString(",") { (k,v) -> "${escape(k)}=${escape(v)}" }
            "approved-shell:${escape(action.commandId)}:$args"
        }
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(":", "\\:")
        .replace(",", "\\,")
        .replace("=", "\\=")
}
