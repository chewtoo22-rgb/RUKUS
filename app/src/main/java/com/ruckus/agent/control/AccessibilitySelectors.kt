package com.ruckus.agent.control

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

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
}
