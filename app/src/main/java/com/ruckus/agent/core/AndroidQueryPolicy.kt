package com.ruckus.agent.core

internal object AndroidQueryPolicy {
    fun <T> readOrEmpty(query: () -> List<T>): List<T> = try {
        query()
    } catch (_: RuntimeException) {
        emptyList()
    }
}
