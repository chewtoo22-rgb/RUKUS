package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionVerifierDeviceTest {
    private val before = "pkg=com.example.app | node[text=Continue;clickable=true;enabled=true]"
    private val changed = "pkg=com.example.app | node[text=Done;clickable=false;enabled=true]"

    @Test
    fun coordinate_gesture_adapter_ack_is_not_completion_proof_without_observed_change() {
        val tap = ActionVerifier.verify(
            AgentAction.Tap(100f, 200f),
            before,
            before,
            "Tapped",
        )
        val swipe = ActionVerifier.verify(
            AgentAction.Swipe(100f, 400f, 100f, 100f),
            before,
            before,
            "Swiped",
        )

        assertFalse(tap.ok)
        assertFalse(swipe.ok)
    }

    @Test
    fun coordinate_gesture_can_be_verified_after_observed_ui_change() {
        val tap = ActionVerifier.verify(
            AgentAction.Tap(100f, 200f),
            before,
            changed,
            "Tapped",
        )
        val swipe = ActionVerifier.verify(
            AgentAction.Swipe(100f, 400f, 100f, 100f),
            before,
            changed,
            "Swiped",
        )

        assertTrue(tap.ok)
        assertTrue(swipe.ok)
    }
}
