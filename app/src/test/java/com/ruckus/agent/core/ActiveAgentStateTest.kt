package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ActiveAgentStateTest {
    @Test
    fun legacyMutinyCanonicalizesToNitro() {
        val controller = ActiveAgentController(AgentIdentity.MUTINY)
        assertEquals(AgentIdentity.NITRO, controller.snapshot().active)
    }

    @Test
    fun installTestHandoffMovesNitroToRukusAndBack() {
        val controller = ActiveAgentController(AgentIdentity.NITRO)

        val delegated = controller.beginInstallTestHandoff()
        assertEquals(AgentIdentity.RUKUS, delegated.active)
        assertEquals(AgentIdentity.NITRO, delegated.returnTarget)
        assertEquals(1L, delegated.revision)

        val returned = controller.completeInstallTestHandoff()
        assertEquals(AgentIdentity.NITRO, returned.active)
        assertNull(returned.returnTarget)
        assertEquals(2L, returned.revision)
    }

    @Test
    fun rukusCannotArmNitroReturnWithoutNitroBeingActive() {
        val controller = ActiveAgentController(AgentIdentity.RUKUS)
        assertThrows(IllegalStateException::class.java) {
            controller.beginInstallTestHandoff()
        }
    }

    @Test
    fun explicitSelectionCancelsPendingAutomaticReturn() {
        val controller = ActiveAgentController(AgentIdentity.NITRO)
        controller.beginInstallTestHandoff()

        val selected = controller.select(AgentIdentity.RUKUS)
        assertEquals(AgentIdentity.RUKUS, selected.active)
        assertNull(selected.returnTarget)

        assertThrows(IllegalStateException::class.java) {
            controller.completeInstallTestHandoff()
        }
    }

    @Test
    fun abortClearsReturnButKeepsCurrentAgent() {
        val controller = ActiveAgentController(AgentIdentity.NITRO)
        controller.beginInstallTestHandoff()

        val aborted = controller.abortHandoff()
        assertEquals(AgentIdentity.RUKUS, aborted.active)
        assertNull(aborted.returnTarget)
        assertEquals(2L, aborted.revision)
    }

    @Test
    fun selectingCurrentAgentWithoutPendingHandoffIsNoOp() {
        val controller = ActiveAgentController(AgentIdentity.RUKUS)
        val original = controller.snapshot()
        val selected = controller.select(AgentIdentity.RUKUS)
        assertEquals(original, selected)
    }
}
