package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningActionCodecTest {
    @Test
    fun `decodes bounded semantic reasoning actions`() {
        val result = ReasoningActionCodec.decode(
            "OPEN_APP_NAME\tSpotify\nTAP_LABEL\tSearch\nTYPE_TEXT\thello\\nworld"
        )

        assertTrue(result.allowed)
        assertEquals(
            listOf(
                AgentAction.OpenAppByName("Spotify"),
                AgentAction.TapLabel("Search"),
                AgentAction.TypeText("hello\nworld"),
            ),
            result.actions,
        )
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
    fun `rejects reasoning actions excluded by reasoning policy`() {
        val result = ReasoningActionCodec.decode("OPEN_APP\t")
        assertFalse(result.allowed)
    }

    @Test
    fun `decodes bounded settings and semantic scroll`() {
        val result = ReasoningActionCodec.decode(
            "SET_BRIGHTNESS\t42\nSET_MEDIA_VOLUME\t25\nSCROLL_DOWN\nINSPECT"
        )

        assertTrue(result.allowed)
        assertEquals(
            listOf(
                AgentAction.SetBrightness(42),
                AgentAction.SetMediaVolume(25),
                AgentAction.Scroll(AgentAction.Direction.DOWN),
                AgentAction.InspectScreen,
            ),
            result.actions,
        )
    }

    @Test
    fun `rejects invalid percentage values`() {
        assertFalse(ReasoningActionCodec.decode("SET_BRIGHTNESS\t101").allowed)
        assertFalse(ReasoningActionCodec.decode("SET_MEDIA_VOLUME\t-1").allowed)
    }
}
