package com.ruckus.agent.control

/**
 * Pure admission boundary for Android capability observations.
 *
 * This class deliberately performs no Android API calls. Callers must collect the
 * platform state separately and pass a bounded, monotonic-time snapshot here.
 */
object PermissionReadiness {
    const val DEFAULT_MAX_AGE_MS: Long = 5_000
    const val MAX_ALLOWED_AGE_MS: Long = 60_000

    enum class Capability {
        ACCESSIBILITY,
        WRITE_SETTINGS,
        SHIZUKU,
    }

    data class Snapshot(
        val accessibilityReady: Boolean,
        val writeSettingsReady: Boolean,
        val shizukuReady: Boolean,
        val observedAtElapsedRealtimeMs: Long,
    )

    enum class BlockReason {
        INVALID_TIME,
        STALE_OBSERVATION,
        ACCESSIBILITY_UNAVAILABLE,
        WRITE_SETTINGS_UNAVAILABLE,
        SHIZUKU_UNAVAILABLE,
    }

    data class Decision(
        val ready: Boolean,
        val blockers: List<BlockReason>,
    )

    fun evaluate(
        snapshot: Snapshot,
        required: Set<Capability>,
        nowElapsedRealtimeMs: Long,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    ): Decision {
        require(maxAgeMs in 0..MAX_ALLOWED_AGE_MS) {
            "maxAgeMs must be between 0 and $MAX_ALLOWED_AGE_MS"
        }

        if (snapshot.observedAtElapsedRealtimeMs < 0 || nowElapsedRealtimeMs < 0 ||
            snapshot.observedAtElapsedRealtimeMs > nowElapsedRealtimeMs
        ) {
            return Decision(false, listOf(BlockReason.INVALID_TIME))
        }

        if (nowElapsedRealtimeMs - snapshot.observedAtElapsedRealtimeMs > maxAgeMs) {
            return Decision(false, listOf(BlockReason.STALE_OBSERVATION))
        }

        val blockers = buildList {
            if (Capability.ACCESSIBILITY in required && !snapshot.accessibilityReady) {
                add(BlockReason.ACCESSIBILITY_UNAVAILABLE)
            }
            if (Capability.WRITE_SETTINGS in required && !snapshot.writeSettingsReady) {
                add(BlockReason.WRITE_SETTINGS_UNAVAILABLE)
            }
            if (Capability.SHIZUKU in required && !snapshot.shizukuReady) {
                add(BlockReason.SHIZUKU_UNAVAILABLE)
            }
        }

        return Decision(blockers.isEmpty(), blockers)
    }
}
