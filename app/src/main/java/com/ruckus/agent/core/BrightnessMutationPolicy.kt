package com.ruckus.agent.core

internal object BrightnessMutationPolicy {
    fun toSystemValue(percent: Int): Int {
        require(percent in 0..100) { "Brightness percent must be between 0 and 100" }
        return (percent * 255 / 100).coerceIn(0, 255)
    }

    fun requireApplied(applied: Boolean, percent: Int) {
        check(applied) { "Android rejected brightness change to $percent%" }
    }
}
