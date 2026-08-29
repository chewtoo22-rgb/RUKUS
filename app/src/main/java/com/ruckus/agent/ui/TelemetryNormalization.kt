package com.ruckus.agent.ui

internal object TelemetryNormalization {
    fun percent(value: Double): Int = when {
        value.isNaN() || value.isInfinite() -> 0
        else -> value.toInt().coerceIn(0, 100)
    }

    fun ratioPercent(used: Long, total: Long): Int {
        if (total <= 0L) return 0
        val boundedUsed = used.coerceIn(0L, total)
        return percent((boundedUsed.toDouble() / total.toDouble()) * 100.0)
    }

    fun batteryPercent(level: Int, scale: Int): Int {
        if (level < 0 || scale <= 0) return 0
        return percent((level.toDouble() / scale.toDouble()) * 100.0)
    }

    fun cpuPercent(oneMinuteLoad: Float, cores: Int): Int {
        if (!oneMinuteLoad.isFinite() || cores <= 0) return 0
        return percent((oneMinuteLoad.toDouble() / cores.toDouble()) * 100.0)
    }

    fun nonNegativeGb(bytes: Long): Float = bytes.coerceAtLeast(0L) / 1_073_741_824f
}
