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
    fun coordinate_gestures_require_observable_effect_even_when_adapter_reports_success() {
        val unchangedTap = ActionVerifier.verify(AgentAction.Tap(10f, 20f), before, before, "Tapped")
        val unchangedSwipe = ActionVerifier.verify(
            AgentAction.Swipe(10f, 20f, 10f, 200f),
            before,
            before,
            "Swiped",
        )
        val changedTap = ActionVerifier.verify(AgentAction.Tap(10f, 20f), before, changed, "Tapped")
        val changedSwipe = ActionVerifier.verify(
            AgentAction.Swipe(10f, 20f, 10f, 200f),
            before,
            changed,
            "Swiped",
        )

        assertFalse(unchangedTap.ok)
        assertFalse(unchangedSwipe.ok)
        assertTrue(changedTap.ok)
        assertTrue(changedSwipe.ok)
    }

    @Test
    fun exact_package_launch_requires_exact_foreground_identity() {
        val action = AgentAction.OpenApp("com.example.app")
        val verified = ActionVerifier.verify(
            action,
            before,
            "pkg=com.example.app | node[text=Home;clickable=true;enabled=true]",
            "Opened com.example.app",
        )
        val prefixSpoof = ActionVerifier.verify(
            action,
            before,
            "pkg=com.example.app.evil | node[text=Home;clickable=true;enabled=true]",
            "Opened com.example.app",
        )

        assertTrue(verified.ok)
        assertFalse(prefixSpoof.ok)
    }

    @Test
    fun named_app_launch_requires_resolved_package_to_be_foreground() {
        val action = AgentAction.OpenAppByName("Spotify")
        val verified = ActionVerifier.verify(
            action,
            before,
            "pkg=com.spotify.music | node[text=Home;clickable=true;enabled=true]",
            "Opened Spotify package=com.spotify.music",
        )
        val wrongForeground = ActionVerifier.verify(
            action,
            before,
            "pkg=com.example.other | node[text=Loading;clickable=false;enabled=true]",
            "Opened Spotify package=com.spotify.music",
        )

        assertTrue(verified.ok)
        assertFalse(wrongForeground.ok)
    }

    @Test
    fun named_app_launch_rejects_package_prefix_spoof() {
        val action = AgentAction.OpenAppByName("Spotify")
        val result = ActionVerifier.verify(
            action,
            before,
            "pkg=com.spotify.music.clone | node[text=Home;clickable=true;enabled=true]",
            "Opened Spotify package=com.spotify.music",
        )

        assertFalse(result.ok)
    }

    @Test
    fun named_app_launch_rejects_generic_ui_change_or_missing_resolved_package() {
        val action = AgentAction.OpenAppByName("Spotify")
        val genericAck = ActionVerifier.verify(
            action,
            before,
            changed,
            "Opened Spotify",
        )
        val nullAck = ActionVerifier.verify(
            action,
            before,
            changed,
            null,
        )

        assertFalse(genericAck.ok)
        assertFalse(nullAck.ok)
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

    @Test
    fun privileged_shell_requires_structured_success_for_exact_approved_command() {
        val action = AgentAction.RunApprovedShell("wifi.toggle", mapOf("enabled" to "true"))
        val verified = ActionVerifier.verify(
            action,
            before,
            changed,
            "status=ok | commandId=wifi.toggle | detail=completed",
        )
        val genericAck = ActionVerifier.verify(action, before, changed, "Command completed")
        val wrongCommand = ActionVerifier.verify(
            action,
            before,
            changed,
            "status=ok | commandId=wifi.reset | detail=completed",
        )
        val failedStatus = ActionVerifier.verify(
            action,
            before,
            changed,
            "status=error | commandId=wifi.toggle | detail=denied",
        )

        assertTrue(verified.ok)
        assertFalse(genericAck.ok)
        assertFalse(wrongCommand.ok)
        assertFalse(failedStatus.ok)
    }
}
