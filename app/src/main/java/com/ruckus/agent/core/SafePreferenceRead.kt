package com.ruckus.agent.core

/**
 * Fail-closed boundary for typed SharedPreferences reads used by recovery state.
 * A malformed/stale preference type must not crash startup or resume handling.
 */
internal object SafePreferenceRead {
    fun stringOrNull(read: () -> String?): String? = try {
        read()
    } catch (_: RuntimeException) {
        null
    }
}
