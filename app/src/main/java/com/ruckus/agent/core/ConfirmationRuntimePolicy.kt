package com.ruckus.agent.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide runtime policy for whether RUKUS may surface confirmation-gated actions.
 *
 * Disabling confirmations never downgrades a high-impact action to SAFE. Instead, confirmation-
 * required actions fail closed before execution until prompts are re-enabled. This keeps the
 * persisted setting meaningful without creating a bypass around SafetyGate.
 */
object ConfirmationRuntimePolicy {
    private val promptsEnabled = AtomicBoolean(true)

    fun setPromptsEnabled(enabled: Boolean) {
        promptsEnabled.set(enabled)
    }

    fun promptsEnabled(): Boolean = promptsEnabled.get()

    internal fun resetForTests() {
        promptsEnabled.set(true)
    }
}
