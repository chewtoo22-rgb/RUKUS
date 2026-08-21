package com.ruckus.agent.core

data class ScreenObservationResult(
    val screen: String?,
    val samples: Int,
    val stable: Boolean,
    val changedFromBefore: Boolean
)

/**
 * Bounded observation loop for asynchronous Android UI transitions.
 *
 * Accessibility snapshots can lag behind a successful launch/tap/gesture. We therefore sample a
 * few times and prefer two consecutive identical post-change snapshots before verification. The
 * loop is deliberately small and caller-controlled so it cannot wait indefinitely.
 */
object ScreenObservationSettler {
    const val MAX_SAMPLES = 5
    const val REQUIRED_STABLE_SAMPLES = 2
    const val SAMPLE_DELAY_MS = 120L

    fun observe(
        before: String?,
        maxSamples: Int = MAX_SAMPLES,
        requiredStableSamples: Int = REQUIRED_STABLE_SAMPLES,
        sampler: () -> String?,
        pause: (Long) -> Unit = {}
    ): ScreenObservationResult {
        require(maxSamples > 0)
        require(requiredStableSamples > 0)
        require(requiredStableSamples <= maxSamples)

        var latest: String? = null
        var previous: String? = null
        var consecutiveStable = 0
        var changed = false

        for(sampleNumber in 1..maxSamples) {
            val current = sampler()
            if(current != null) {
                latest = current
                if(current != before) changed = true
                consecutiveStable = if(current == previous) consecutiveStable + 1 else 1
                previous = current

                // When we have a baseline, do not settle on stale pre-action UI. Wait for either
                // a visible change or exhaustion of the bounded sampling window.
                val eligibleToSettle = before == null || changed
                if(eligibleToSettle && consecutiveStable >= requiredStableSamples) {
                    return ScreenObservationResult(current,sampleNumber,true,changed)
                }
            } else {
                consecutiveStable = 0
                previous = null
            }

            if(sampleNumber < maxSamples) pause(SAMPLE_DELAY_MS)
        }

        return ScreenObservationResult(latest ?: before,maxSamples,false,changed)
    }
}
