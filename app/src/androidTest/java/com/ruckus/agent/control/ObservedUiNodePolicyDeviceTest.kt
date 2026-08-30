package com.ruckus.agent.control

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservedUiNodePolicyDeviceTest {
    @Test
    fun focusedEditorAndScrollTargetRemainObservableUnderLargeUiTree() {
        val ordinary = (0 until 24).map { index -> node(text = "Visible row $index") }
        val editor = node(text = "Thursday test", editable = true, focused = true)
        val scroller = node(contentDescription = "Conversation", scrollable = true)

        val selected = ObservedUiNodePolicy.select(ordinary + editor + scroller)

        assertEquals(ObservedUiNodePolicy.MAX_NODES, selected.size)
        assertTrue(selected.contains(editor))
        assertTrue(selected.contains(scroller))
    }

    private fun node(
        text: String? = null,
        contentDescription: String? = null,
        editable: Boolean = false,
        focused: Boolean = false,
        scrollable: Boolean = false,
    ) = UiNodeSnapshot(
        text = text,
        contentDescription = contentDescription,
        viewId = null,
        className = null,
        clickable = false,
        enabled = true,
        editable = editable,
        sensitive = false,
        focused = focused,
        scrollable = scrollable,
    )
}
