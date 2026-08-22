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

    @Test
    fun type_text_requires_requested_text_to_newly_appear() {
        val beforeTyping = "pkg=com.example.app | node[text=Message;editable=true;focused=true;enabled=true;sensitive=false]"
        val afterTyping = "pkg=com.example.app | node[text=Hello Miyagi;editable=true;focused=true;enabled=true;sensitive=false]"

        val verified = ActionVerifier.verify(
            AgentAction.TypeText("Hello Miyagi"),
            beforeTyping,
            afterTyping,
            "Text set",
        )

        assertTrue(verified.ok)
    }

    @Test
    fun type_text_rejects_preexisting_matching_text_even_with_adapter_success() {
        val preexisting = "pkg=com.example.app | node[text=Hello Miyagi;editable=true;focused=true;enabled=true;sensitive=false]"
        val changedElsewhere = "$preexisting | node[text=Other UI changed;clickable=false;enabled=true]"

        val unchanged = ActionVerifier.verify(
            AgentAction.TypeText("Hello Miyagi"),
            preexisting,
            preexisting,
            "Text set",
        )
        val unrelatedChange = ActionVerifier.verify(
            AgentAction.TypeText("Hello Miyagi"),
            preexisting,
            changedElsewhere,
            "Text set",
        )

        assertFalse(unchanged.ok)
        assertFalse(unrelatedChange.ok)
    }

    @Test
    fun type_text_rejects_adapter_ack_without_observable_requested_text() {
        val afterUnrelatedChange = "pkg=com.example.app | node[text=Different value;editable=true;focused=true;enabled=true;sensitive=false]"

        val result = ActionVerifier.verify(
            AgentAction.TypeText("Hello Miyagi"),
            before,
            afterUnrelatedChange,
            "Text set",
        )

        assertFalse(result.ok)
    }

    @Test
    fun brightness_requires_observed_requested_raw_value() {
        val action = AgentAction.SetBrightness(42)
        val expectedRaw = (42 * 255 / 100).coerceIn(1,255)
        val verified = ActionVerifier.verify(
            action,
            before,
            "pkg=com.example.app | state[brightness=$expectedRaw;media=4;mediaMax=15]",
            "Brightness 42%",
        )
        val mismatch = ActionVerifier.verify(
            action,
            before,
            "pkg=com.example.app | state[brightness=${expectedRaw + 1};media=4;mediaMax=15]",
            "Brightness 42%",
        )

        assertTrue(verified.ok)
        assertFalse(mismatch.ok)
    }

    @Test
    fun brightness_rejects_adapter_ack_without_observed_state() {
        val result = ActionVerifier.verify(
            AgentAction.SetBrightness(42),
            before,
            changed,
            "Brightness 42%",
        )

        assertFalse(result.ok)
    }

    @Test
    fun media_volume_verifies_quantized_stream_value() {
        val action = AgentAction.SetMediaVolume(30)
        val verified = ActionVerifier.verify(
            action,
            before,
            "pkg=com.example.app | state[brightness=120;media=4;mediaMax=15]",
            "Media 30%",
        )
        val mismatch = ActionVerifier.verify(
            action,
            before,
            "pkg=com.example.app | state[brightness=120;media=5;mediaMax=15]",
            "Media 30%",
        )

        assertTrue(verified.ok)
        assertFalse(mismatch.ok)
    }

    @Test
    fun media_volume_rejects_missing_observed_maximum() {
        val result = ActionVerifier.verify(
            AgentAction.SetMediaVolume(30),
            before,
            "pkg=com.example.app | state[brightness=120;media=4]",
            "Media 30%",
        )

        assertFalse(result.ok)
    }
}
