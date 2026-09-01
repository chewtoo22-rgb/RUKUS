package com.ruckus.agent.control

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ActiveInstanceRegistryTest {
    @Test
    fun `register exposes current instance`() {
        val registry = ActiveInstanceRegistry<Any>()
        val first = Any()

        registry.register(first)

        assertSame(first, registry.current())
    }

    @Test
    fun `unregister clears matching active instance`() {
        val registry = ActiveInstanceRegistry<Any>()
        val first = Any()
        registry.register(first)

        registry.unregister(first)

        assertNull(registry.current())
    }

    @Test
    fun `late teardown of old instance cannot clear replacement`() {
        val registry = ActiveInstanceRegistry<Any>()
        val old = Any()
        val replacement = Any()
        registry.register(old)
        registry.register(replacement)

        registry.unregister(old)

        assertSame(replacement, registry.current())
    }

    @Test
    fun `replacement teardown clears replacement`() {
        val registry = ActiveInstanceRegistry<Any>()
        val old = Any()
        val replacement = Any()
        registry.register(old)
        registry.register(replacement)

        registry.unregister(replacement)

        assertNull(registry.current())
    }
}
