package com.ruckus.agent.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedUiNodePolicyTest {
    @Test
    fun `focused editable node survives beyond normal observation cap`() {
        val ordinary = (0 until 20).map { index -> node(text = "Row $index") }
        val focusedEditor = node(text = "Draft reply", editable = true, focused = true)

        val selected = ObservedUiNodePolicy.select(ordinary + focusedEditor)

        assertEquals(ObservedUiNodePolicy.MAX_NODES, selected.size)
        assertTrue(selected.contains(focusedEditor))
    }

    @Test
    fun `scroll target survives beyond normal observation cap`() {
        val ordinary = (0 until 20).map { index -> node(text = "Item $index") }
        val scrollTarget = node(contentDescription = "Feed", scrollable = true)

        val selected = ObservedUiNodePolicy.select(ordinary + scrollTarget)

        assertEquals(ObservedUiNodePolicy.MAX_NODES, selected.size)
        assertTrue(selected.contains(scrollTarget))
    }

    @Test
    fun `selection stays deterministic bounded and filters irrelevant duplicates`() {
        val duplicate = node(text = "Same")
        val irrelevant = node()
        val source = listOf(irrelevant, duplicate, duplicate) +
            (0 until 20).map { index -> node(text = "Node $index") }

        val first = ObservedUiNodePolicy.select(source)
        val second = ObservedUiNodePolicy.select(source)

        assertEquals(first, second)
        assertTrue(first.size <= ObservedUiNodePolicy.MAX_NODES)
        assertEquals(1, first.count { it == duplicate })
        assertTrue(first.none { it == irrelevant })
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
