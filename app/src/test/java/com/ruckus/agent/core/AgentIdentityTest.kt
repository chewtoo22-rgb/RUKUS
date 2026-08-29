package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentIdentityTest {
    private val kernel = AgentKernel()

    @Test
    fun nitroIsCanonicalBuilderIdentity() {
        assertEquals(AgentIdentity.NITRO, AgentIdentity.NITRO.canonical())
        assertEquals(
            listOf(
                "create_project", "plan_project", "edit_project", "build_apk",
                "analyze_build", "game_2d", "game_3d", "install_test_via_rukus"
            ),
            kernel.capabilities(AgentIdentity.NITRO)
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun legacyMutinyIdentityMapsToNitroWithoutCapabilityDrift() {
        assertEquals(AgentIdentity.NITRO, AgentIdentity.MUTINY.canonical())
        assertEquals(
            kernel.capabilities(AgentIdentity.NITRO),
            kernel.capabilities(AgentIdentity.MUTINY)
        )
    }

    @Test
    fun rukusCapabilitiesRemainIndependent() {
        val rukus = kernel.capabilities(AgentIdentity.RUKUS)
        val nitro = kernel.capabilities(AgentIdentity.NITRO)
        assertEquals(false, rukus.contains("build_apk"))
        assertEquals(false, nitro.contains("approved_shizuku"))
    }
}
