package com.ruckus.agent.control

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.ruckus.agent.core.AgentAction

object AccessibilitySelectors {
    fun flatten(root: AccessibilityNodeInfo?): List<UiNodeSnapshot> {
        if (root == null) return emptyList()
        val out = mutableListOf<UiNodeSnapshot>()
        fun walk(node: AccessibilityNodeInfo) {
            out += UiNodeSnapshot(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                clickable = node.isClickable,
                enabled = node.isEnabled,
                editable = node.isEditable,
                sensitive = node.isPassword,
                focused = node.isFocused,
                scrollable = node.isScrollable,
            )
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        walk(root)
        return out
    }

    fun clickByLabel(root: AccessibilityNodeInfo?, label: String): Boolean {
        if (root == null) return false
        val candidates = root.findAccessibilityNodeInfosByText(label)
        val target = candidates.firstOrNull() ?: return false
        var node: AccessibilityNodeInfo? = target
        while (node != null) {
            if (node.isClickable && node.isEnabled) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            node = node.parent
        }
        return false
    }

    fun typeIntoFocused(root: AccessibilityNodeInfo?, text: String): Boolean {
        if (root == null) return false
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!focus.isEnabled || !focus.isEditable || focus.isPassword) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Executes semantic scrolling on the same uniquely grounded accessibility container admitted
     * by ReasoningGroundingPolicy. Refuses ambiguous or missing targets instead of falling back to
     * a screen-coordinate swipe that could move the wrong surface.
     */
    fun scrollUnique(root: AccessibilityNodeInfo?, direction: AgentAction.Direction): Boolean {
        if (root == null) return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun walk(node: AccessibilityNodeInfo) {
            if (node.isEnabled && node.isScrollable) candidates += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        walk(root)

        if (candidates.size != 1) return false
        val action = when (direction) {
            AgentAction.Direction.DOWN -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            AgentAction.Direction.UP -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return candidates.single().performAction(action)
    }
}
