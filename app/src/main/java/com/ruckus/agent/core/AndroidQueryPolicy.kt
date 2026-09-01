package com.ruckus.agent.core

internal object AndroidQueryPolicy {
    fun <T> readOrEmpty(query: () -> List<T>): List<T> = readOrDefault(emptyList(), query)

    fun <T> readOrDefault(default: T, query: () -> T): T = try {
        query()
    } catch (_: RuntimeException) {
        default
    }
}
