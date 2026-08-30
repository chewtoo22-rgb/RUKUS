package com.ruckus.agent.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ObservationRedactionPolicyTest {
    @Test
    fun `sensitive text never crosses observation label boundary`() {
        val node = node(text = "hunter2", contentDescription = "Password value", sensitive = true)

        val label = ObservationRedactionPolicy.label(node)

        assertEquals(ObservationRedactionPolicy.SENSITIVE_LABEL, label)
        assertFalse(label.contains("hunter2"))
        assertFalse(label.contains("Password value"))
    }

    @Test
    fun `non-sensitive nodes keep trimmed semantic labels`() {
        assertEquals("Save", ObservationRedactionPolicy.label(node(text = "  Save  ")))
        assertEquals("Continue", ObservationRedactionPolicy.label(node(contentDescription = " Continue ")))
    }

    @Test
    fun `sensitive focused editor keeps only structural redaction marker`() {
        val node = node(text = "1234", editable = true, focused = true, sensitive = true)

        assertEquals(ObservationRedactionPolicy.SENSITIVE_LABEL, ObservationRedactionPolicy.label(node))
    }

    private fun node(
        text: String? = null,
        contentDescription: String? = null,
        editable: Boolean = false,
        focused: Boolean = false,
        sensitive: Boolean = false,
    ) = UiNodeSnapshot(
        text = text,
        contentDescription = contentDescription,
        viewId = null,
        className = null,
        clickable = false,
        enabled = true,
        editable = editable,
        sensitive = sensitive,
        focused = focused,
        scrollable = false,
    )
}
