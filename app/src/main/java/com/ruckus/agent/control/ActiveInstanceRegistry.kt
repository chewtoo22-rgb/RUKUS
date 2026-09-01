package com.ruckus.agent.control

internal class ActiveInstanceRegistry<T : Any> {
    @Volatile
    private var active: T? = null

    fun current(): T? = active

    @Synchronized
    fun register(instance: T) {
        active = instance
    }

    @Synchronized
    fun unregister(instance: T) {
        if (active === instance) {
            active = null
        }
    }
}
