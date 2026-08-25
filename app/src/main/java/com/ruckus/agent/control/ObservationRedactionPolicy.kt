package com.ruckus.agent.control

/**
 * Prevents sensitive accessibility content from crossing the observation boundary.
 *
 * Password/PIN fields may still need to remain structurally visible so the executor can reason
 * about focus and sensitivity, but their text/content descriptions must never flow into reasoning,
 * audit, persistence, or completion evidence.
 */
object ObservationRedactionPolicy {
    const val SENSITIVE_LABEL = "<sensitive>"

    fun label(node: UiNodeSnapshot): String {
        if (node.sensitive) return SENSITIVE_LABEL

        return node.text?.trim()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.trim()?.takeIf { it.isNotBlank() }
            ?: ""
    }
}
