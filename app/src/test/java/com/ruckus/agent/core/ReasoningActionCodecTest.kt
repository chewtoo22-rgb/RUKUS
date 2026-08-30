package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningActionCodecTest {
    @Test
    fun `decodes bounded semantic reasoning action`() {
        val result = ReasoningActionCodec.decode(
            "INSPECT\nTYPE_TEXT\thello\\nworld"
        )

        assertTrue(result.allowed)
        assertEquals(
            listOf(
                AgentAction.InspectScreen,
                AgentAction.TypeText("hello\nworld"),
            ),
            result.actions,
        )
    }

    @Test
    fun `decodes grounded app and semantic tap primitives individually`() {
        val app = ReasoningActionCodec.decode("OPEN_APP_NAME\tSpotify")
        val tap = ReasoningActionCodec.decode("TAP_LABEL\tSearch")

        assertTrue(app.allowed)
        assertEquals(listOf(AgentAction.OpenAppByName("Spotify")), app.actions)
        assertTrue(tap.allowed)
        assertEquals(listOf(AgentAction.TapLabel("Search")), tap.actions)
    }

    @Test
    fun `rejects raw coordinate and privileged verbs`() {
        assertFalse(ReasoningActionCodec.decode("TAP\t10\t20").allowed)
        assertFalse(ReasoningActionCodec.decode("SWIPE\t0\t0\t100\t100").allowed)
        assertFalse(ReasoningActionCodec.decode("RUN_SHELL\tid").allowed)
    }

    @Test
    fun `rejects unknown and malformed escaping`() {
        assertFalse(ReasoningActionCodec.decode("DO_MAGIC\tnow").allowed)
        assertFalse(ReasoningActionCodec.decode("TYPE_TEXT\tbad\\qescape").allowed)
        assertFalse(ReasoningActionCodec.decode("HOME\textra").allowed)
    }

    @Test
    fun `rejects overlong plans before typed execution`() {
        val payload = List(PlanAdmissionPolicy.MAX_ACTIONS + 1) { "INSPECT" }.joinToString("\n")
        val result = ReasoningActionCodec.decode(payload)

        assertFalse(result.allowed)
        assertTrue(result.error.orEmpty().contains("exceeds"))
    }

    @Test
    fun `rejects multiple state changes from one observation`() {
        val result = ReasoningActionCodec.decode(
            "OPEN_APP_NAME\tSpotify\nTAP_LABEL\tSearch"
        )

        assertFalse(result.allowed)
        assertTrue(result.error.orEmpty().contains("state-changing", ignoreCase = true))
        assertTrue(result.error.orEmpty().contains("replan", ignoreCase = true))
    }

    @Test
    fun `rejects reasoning actions excluded by reasoning policy`() {
        val result = ReasoningActionCodec.decode("OPEN_APP\t")
        assertFalse(result.allowed)
    }

    @Test
    fun `decodes bounded settings and semantic scroll individually`() {
        val brightness = ReasoningActionCodec.decode("SET_BRIGHTNESS\t42")
        val volume = ReasoningActionCodec.decode("SET_MEDIA_VOLUME\t25")
        val scroll = ReasoningActionCodec.decode("SCROLL_DOWN\nINSPECT")

        assertTrue(brightness.allowed)
        assertEquals(listOf(AgentAction.SetBrightness(42)), brightness.actions)

        assertTrue(volume.allowed)
        assertEquals(listOf(AgentAction.SetMediaVolume(25)), volume.actions)

        assertTrue(scroll.allowed)
        assertEquals(
            listOf(
                AgentAction.Scroll(AgentAction.Direction.DOWN),
                AgentAction.InspectScreen,
            ),
            scroll.actions,
        )
    }

    @Test
    fun `rejects invalid percentage values`() {
        assertFalse(ReasoningActionCodec.decode("SET_BRIGHTNESS\t101").allowed)
        assertFalse(ReasoningActionCodec.decode("SET_MEDIA_VOLUME\t-1").allowed)
    }
}
