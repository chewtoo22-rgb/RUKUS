package com.ruckus.agent.core

/**
 * Fail-closed boundary around Android launch-target discovery.
 *
 * Package visibility/provider failures must never escape command preflight and crash the executor.
 * A failed probe is equivalent to an unavailable launch target, so no earlier side effect begins.
 */
object LaunchTargetReadiness {
    fun probe(check: () -> Boolean): Boolean = try {
        check()
    } catch (_: Exception) {
        false
    }
}
