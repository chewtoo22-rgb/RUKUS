package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionVerifierSemanticEffectTest {
    private val before = "pkg=com.example.app | node[text=Continue;clickable=true;enabled=true]"
    private val changed = "pkg=com.example.app | node[text=Done;clickable=false;enabled=true]"

    @Test
    fun semantic_tap_requires_observable_effect_even_when_adapter_reports_success() {
        val unchanged = ActionVerifier.verify(
            AgentAction.TapLabel("Continue"),
            before,
            before,
            "Tapped Continue",
        )
        val changedUi = ActionVerifier.verify(
            AgentAction.TapLabel("Continue"),
            before,
            changed,
            "Tapped Continue",
        )

        assertFalse(unchanged.ok)
        assertTrue(changedUi.ok)
    }

    @Test
    fun semantic_scroll_requires_observable_effect_even_when_adapter_reports_success() {
        val unchanged = ActionVerifier.verify(
            AgentAction.Scroll(AgentAction.Direction.DOWN),
            before,
            before,
            "Scrolled down",
        )
        val changedUi = ActionVerifier.verify(
            AgentAction.Scroll(AgentAction.Direction.DOWN),
            before,
            changed,
            "Scrolled down",
        )

        assertFalse(unchanged.ok)
        assertTrue(changedUi.ok)
    }

    @Test
    fun raw_coordinate_actions_keep_adapter_acknowledgement_fallback() {
        val tap = ActionVerifier.verify(AgentAction.Tap(10f, 20f), before, before, "Tapped")
        val swipe = ActionVerifier.verify(
            AgentAction.Swipe(10f, 20f, 10f, 200f),
            before,
            before,
            "Swiped",
        )

        assertTrue(tap.ok)
        assertTrue(swipe.ok)
    }
}
