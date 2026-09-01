package com.ruckus.agent.core

internal object MediaVolumeMutationPolicy {
    fun targetIndex(percent: Int, maxIndex: Int): Int {
        require(percent in 0..100) { "Media volume percent must be between 0 and 100" }
        require(maxIndex >= 0) { "Media volume max index must be non-negative" }
        return ((maxIndex.toLong() * percent) / 100L).toInt().coerceIn(0, maxIndex)
    }

    fun requireApplied(expectedIndex: Int, actualIndex: Int, percent: Int) {
        check(actualIndex == expectedIndex) {
            "Android rejected media volume change to $percent% (expected index=$expectedIndex, actual=$actualIndex)"
        }
    }
}
